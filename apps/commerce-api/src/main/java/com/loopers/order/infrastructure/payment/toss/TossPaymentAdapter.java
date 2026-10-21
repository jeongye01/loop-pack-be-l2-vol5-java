package com.loopers.order.infrastructure.payment.toss;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.order.domain.PaymentGateway;
import com.loopers.order.domain.PaymentGateway.CancellationConfirmed;
import com.loopers.order.domain.PaymentGateway.CancellationRejected;
import com.loopers.order.domain.PaymentGateway.CancellationRequest;
import com.loopers.order.domain.PaymentGateway.CancellationResult;
import com.loopers.order.domain.PaymentGateway.CancellationUnknown;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.time.OffsetDateTime;
import java.util.Map;

public final class TossPaymentAdapter implements PaymentGateway {

    private final RestClient client;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public TossPaymentAdapter(RestClient client) {
        this.client = client;
    }

    @Override
    public ApprovalResult approve(ApprovalRequest request) {
        try {
            return client.post()
                .uri("/v1/payments/confirm")
                .header("Idempotency-Key", request.idempotencyKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                    "paymentKey", request.paymentKey(),
                    "orderId", request.providerOrderId(),
                    "amount", request.amount()
                ))
                .exchange((httpRequest, response) -> {
                    int status = response.getStatusCode().value();
                    if (response.getStatusCode().is5xxServerError()) {
                        return new Unknown();
                    }
                    JsonNode body;
                    try {
                        body = objectMapper.readTree(response.getBody());
                    } catch (IOException exception) {
                        return new Unknown();
                    }
                    return status == 200 ? approved(body) : providerError(status, body);
                });
        } catch (RestClientException exception) {
            for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
                if (cause instanceof SocketTimeoutException) {
                    return new Unknown();
                }
            }
            throw exception;
        }
    }

    @Override
    public ApprovalResult findByPaymentKey(String paymentKey) {
        try {
            return client.get()
                .uri("/v1/payments/{paymentKey}", paymentKey)
                .exchange((httpRequest, response) -> {
                    int status = response.getStatusCode().value();
                    if (response.getStatusCode().is5xxServerError()) {
                        return new Unknown();
                    }
                    JsonNode body;
                    try {
                        body = objectMapper.readTree(response.getBody());
                    } catch (IOException exception) {
                        return new Unknown();
                    }
                    if (status == 404) {
                        return providerError(status, body);
                    }
                    if (status != 200) {
                        return providerError(status, body);
                    }
                    String paymentStatus = text(body, "status");
                    if ("DONE".equals(paymentStatus)) {
                        return approved(body);
                    }
                    if ("CANCELED".equals(paymentStatus) || "PARTIAL_CANCELED".equals(paymentStatus)
                        || "ABORTED".equals(paymentStatus) || "EXPIRED".equals(paymentStatus)) {
                        return new ProviderError(status, paymentStatus, paymentStatus, body.toString());
                    }
                    return new Unknown(body.toString());
                });
        } catch (RestClientException exception) {
            return new Unknown();
        }
    }

    @Override
    public CancellationResult cancel(CancellationRequest request) {
        try {
            return client.post()
                .uri("/v1/payments/{paymentKey}/cancel", request.paymentKey())
                .header("Idempotency-Key", request.idempotencyKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("cancelReason", request.cancelReason()))
                .exchange((httpRequest, response) -> {
                    int status = response.getStatusCode().value();
                    JsonNode body;
                    try {
                        body = objectMapper.readTree(response.getBody());
                    } catch (IOException exception) {
                        return new CancellationUnknown();
                    }
                    if (status == 200 && "CANCELED".equals(text(body, "status"))) {
                        return new CancellationConfirmed(request.paymentKey(), body.toString());
                    }
                    if (response.getStatusCode().is5xxServerError()) {
                        return new CancellationUnknown(body.toString());
                    }
                    if (response.getStatusCode().is4xxClientError()) {
                        return new CancellationRejected(status, text(body, "code"), text(body, "message"),
                            body.toString());
                    }
                    return new CancellationUnknown(body.toString());
                });
        } catch (RestClientException exception) {
            return new CancellationUnknown();
        }
    }

    private ApprovalResult approved(JsonNode body) {
        return new Approved(text(body, "paymentKey"), text(body, "orderId"),
            body.path("totalAmount").longValue(), OffsetDateTime.parse(text(body, "approvedAt")));
    }

    private ApprovalResult providerError(int status, JsonNode body) {
        return new ProviderError(status, body == null ? null : text(body, "code"),
            body == null ? null : text(body, "message"));
    }

    private String text(JsonNode body, String field) {
        JsonNode value = body.path(field);
        return value.isTextual() ? value.textValue() : null;
    }
}
