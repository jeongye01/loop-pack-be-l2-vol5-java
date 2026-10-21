package com.loopers.order.infrastructure.payment.toss;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import com.loopers.order.domain.PaymentGateway.ApprovalRequest;

import java.net.URI;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

/** 승인 HTTP 계약용 대역. 결제 원장이나 주문·재고 처리는 구현하지 않는다. */
public final class TossPgFixture implements AutoCloseable {

    public static final String SECRET_KEY = "local-secret";
    private static final String APPROVAL_PATH = "/v1/payments/confirm";
    private static final Set<String> ERROR_CODES = Set.of(
        "ALREADY_PROCESSED_PAYMENT", "ALREADY_PROCESSING_REQUEST",
        "PROVIDER_ERROR", "INVALID_REQUEST"
    );

    private final ObjectMapper mapper = new ObjectMapper();
    private final WireMockServer server = new WireMockServer(wireMockConfig()
        .bindAddress("127.0.0.1").dynamicPort());

    public TossPgFixture() {
        server.start();
    }

    public URI baseUrl() {
        return URI.create("http://127.0.0.1:" + server.port());
    }

    public void approve(ApprovalRequest request, OffsetDateTime approvedAt) {
        delayedApproval(request, approvedAt, Duration.ZERO);
    }

    public void delayedApproval(ApprovalRequest request, OffsetDateTime approvedAt, Duration delay) {
        respond(request, 200, json(Map.of(
            "paymentKey", request.paymentKey(),
            "orderId", request.providerOrderId(),
            "totalAmount", request.amount(),
            "status", "DONE",
            "approvedAt", approvedAt.toString()
        )), delay);
    }

    public void error(ApprovalRequest request, String code, String message) {
        if (!ERROR_CODES.contains(code)) {
            throw new IllegalArgumentException("Unsupported fixture error: " + code);
        }
        respond(request, 400, json(Map.of("code", code, "message", message)), Duration.ZERO);
    }

    /** 깨진 JSON·필드 불일치·계약 밖 HTTP 응답을 주입하는 테스트에 사용한다. */
    public void respond(ApprovalRequest request, int httpStatus, String body, Duration delay) {
        if (delay.isNegative()) {
            throw new IllegalArgumentException("Response delay must not be negative");
        }
        int delayMillis = Math.toIntExact(delay.toMillis());
        // 설정을 바꾸어도 실제 호출 기록은 보존한다.
        server.resetMappings();
        server.stubFor(post(urlEqualTo(APPROVAL_PATH))
            .withBasicAuth(SECRET_KEY, "")
            .withHeader("Idempotency-Key", equalTo(request.idempotencyKey()))
            .withHeader("Content-Type", matching("application/json.*"))
            .withRequestBody(equalToJson(json(Map.of(
                "paymentKey", request.paymentKey(),
                "orderId", request.providerOrderId(),
                "amount", request.amount()
            ))))
            .willReturn(aResponse().withStatus(httpStatus)
                .withHeader("Content-Type", "application/json")
                .withBody(body).withFixedDelay(delayMillis)));
    }

    public List<LoggedRequest> approvalRequests() {
        return server.findAll(postRequestedFor(urlEqualTo(APPROVAL_PATH)));
    }

    public List<LoggedRequest> unmatchedRequests() {
        return server.findAllUnmatchedRequests();
    }

    @Override
    public void close() {
        server.stop();
    }

    private String json(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Cannot serialize fixture data", exception);
        }
    }
}
