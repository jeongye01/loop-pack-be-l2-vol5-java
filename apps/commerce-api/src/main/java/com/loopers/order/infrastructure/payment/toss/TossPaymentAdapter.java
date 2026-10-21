package com.loopers.order.infrastructure.payment.toss;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.order.domain.PaymentGateway;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Set;

public final class TossPaymentAdapter implements PaymentGateway {

    private static final Set<String> ERROR_CODES = Set.of(
        "ALREADY_PROCESSED_PAYMENT", "ALREADY_PROCESSING_REQUEST",
        "PROVIDER_ERROR", "INVALID_REQUEST"
    );

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
                    if (status != 200 && status != 400) {
                        return new Unknown();
                    }
                    JsonNode body;
                    try {
                        body = objectMapper.readTree(response.getBody());
                    } catch (IOException exception) {
                        return new Unknown();
                    }
                    return status == 200 ? approved(request, body) : providerError(body);
                });
        } catch (RestClientException exception) {
            return new Unknown();
        }
    }

    private ApprovalResult approved(ApprovalRequest request, JsonNode body) {
        if (body == null
            || !request.paymentKey().equals(text(body, "paymentKey"))
            || !request.providerOrderId().equals(text(body, "orderId"))
            || !"DONE".equals(text(body, "status"))
            || !body.path("totalAmount").isNumber()
            || body.path("totalAmount").decimalValue().compareTo(BigDecimal.valueOf(request.amount())) != 0) {
            return new Unknown();
        }
        String approvedAt = text(body, "approvedAt");
        if (approvedAt == null) {
            return new Unknown();
        }
        try {
            return new Approved(request.paymentKey(), request.providerOrderId(),
                request.amount(), OffsetDateTime.parse(approvedAt));
        } catch (DateTimeParseException exception) {
            return new Unknown();
        }
    }

    private ApprovalResult providerError(JsonNode body) {
        if (body == null) {
            return new Unknown();
        }
        String code = text(body, "code");
        String message = text(body, "message");
        if (code == null || message == null || !ERROR_CODES.contains(code)) {
            return new Unknown();
        }
        return new ProviderError(400, code, message);
    }

    private String text(JsonNode body, String field) {
        JsonNode value = body.path(field);
        return value.isTextual() ? value.textValue() : null;
    }
}
