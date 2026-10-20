package com.loopers.order.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.config.TossHttpClientFactory;
import com.loopers.order.domain.PaymentGateway;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class TossPaymentAdapterTest {

    private static final PaymentGateway.ApprovalRequest REQUEST =
        new PaymentGateway.ApprovalRequest("test-payment-42", "LP-ORD-0000000042", 7000);
    private static final String SUCCESS = """
        {"paymentKey":"test-payment-42","orderId":"LP-ORD-0000000042",
         "totalAmount":7000,"status":"DONE","approvedAt":"2026-10-21T15:00:00+09:00"}
        """;

    private final ObjectMapper mapper = new ObjectMapper();
    private final AtomicInteger calls = new AtomicInteger();
    private final AtomicReference<CapturedRequest> captured = new AtomicReference<>();
    private final CountDownLatch releaseResponse = new CountDownLatch(1);
    private HttpServer server;
    private ExecutorService executor;
    private TossPaymentAdapter adapter;
    private volatile int responseStatus = 200;
    private volatile String responseBody = SUCCESS;
    private volatile boolean holdResponse;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        executor = Executors.newVirtualThreadPerTaskExecutor();
        server.setExecutor(executor);
        server.createContext("/", exchange -> {
            calls.incrementAndGet();
            captured.set(new CapturedRequest(
                exchange.getRequestMethod(), exchange.getRequestURI().toString(),
                exchange.getRequestHeaders().getFirst("Authorization"),
                exchange.getRequestHeaders().getFirst("Content-Type"),
                mapper.readTree(exchange.getRequestBody())
            ));
            try {
                if (holdResponse && !releaseResponse.await(3, TimeUnit.SECONDS)) {
                    return;
                }
                byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(responseStatus, bytes.length);
                exchange.getResponseBody().write(bytes);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        server.start();
        adapter = new TossPaymentAdapter(TossHttpClientFactory.create(
            URI.create("http://127.0.0.1:" + server.getAddress().getPort()),
            "local-secret", Duration.ofSeconds(1), Duration.ofSeconds(1)
        ));
    }

    @AfterEach
    void tearDown() {
        releaseResponse.countDown();
        server.stop(0);
        executor.shutdownNow();
        executor.close();
    }

    @Test
    void sendsApprovalContractAndConvertsSuccess() {
        assertThat(adapter.approve(REQUEST)).isEqualTo(new PaymentGateway.Approved(
            REQUEST.paymentKey(), REQUEST.providerOrderId(), 7000,
            OffsetDateTime.parse("2026-10-21T15:00:00+09:00")
        ));
        CapturedRequest http = captured.get();
        assertThat(http.method()).isEqualTo("POST");
        assertThat(http.path()).isEqualTo("/v1/payments/confirm");
        assertThat(http.authorization()).isEqualTo("Basic " + Base64.getEncoder()
            .encodeToString("local-secret:".getBytes(StandardCharsets.UTF_8)));
        assertThat(http.contentType()).startsWith("application/json");
        assertThat(http.body().size()).isEqualTo(3);
        assertThat(http.body().get("paymentKey").textValue()).isEqualTo(REQUEST.paymentKey());
        assertThat(http.body().get("orderId").textValue()).isEqualTo(REQUEST.providerOrderId());
        assertThat(http.body().get("amount").isIntegralNumber()).isTrue();
        assertThat(http.body().get("amount").longValue()).isEqualTo(7000);
        assertThat(calls).hasValue(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "ALREADY_PROCESSED_PAYMENT", "ALREADY_PROCESSING_REQUEST",
        "PROVIDER_ERROR", "INVALID_REQUEST"
    })
    void preservesProviderErrorWithoutDeclaringPaymentFailed(String code) {
        responseStatus = 400;
        responseBody = "{\"code\":\"" + code + "\",\"message\":\"테스트 오류\"}";

        assertThat(adapter.approve(REQUEST))
            .isEqualTo(new PaymentGateway.ProviderError(400, code, "테스트 오류"));
        assertThat(calls).hasValue(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{\"code\":\"INVALID_REQUEST\"}",
        "{\"code\":\"UNRECOGNIZED\",\"message\":\"unknown\"}",
        "not-json", ""
    })
    void incompleteOrUnrecognizedErrorIsUnknown(String body) {
        responseStatus = 400;
        responseBody = body;

        assertThat(adapter.approve(REQUEST)).isInstanceOf(PaymentGateway.Unknown.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "\"paymentKey\":\"other\"", "\"orderId\":\"other\"", "\"totalAmount\":6999",
        "\"totalAmount\":7000.5", "\"totalAmount\":\"7000\"", "\"status\":\"IN_PROGRESS\"",
        "\"approvedAt\":null", "\"approvedAt\":\"invalid\""
    })
    void mismatchedOrInvalidSuccessIsNotApproved(String replacement) throws IOException {
        JsonNode replacementNode = mapper.readTree("{" + replacement + "}");
        var body = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(SUCCESS);
        replacementNode.fields().forEachRemaining(entry -> body.set(entry.getKey(), entry.getValue()));
        responseBody = body.toString();

        assertThat(adapter.approve(REQUEST)).isInstanceOf(PaymentGateway.Unknown.class);
    }

    @Test
    void acceptsAdditionalFieldsAndEquivalentNumericAmount() {
        responseBody = SUCCESS.replace("\"totalAmount\":7000", "\"extra\":true,\"totalAmount\":7000.0");

        assertThat(adapter.approve(REQUEST)).isInstanceOf(PaymentGateway.Approved.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "null", "{}", "[]", "not-json"})
    void malformedSuccessIsUnknown(String body) {
        responseBody = body;

        assertThat(adapter.approve(REQUEST)).isInstanceOf(PaymentGateway.Unknown.class);
    }

    @Test
    void unexpectedHttpStatusDoesNotBecomeSuccess() {
        responseStatus = 500;
        responseBody = SUCCESS;

        assertThat(adapter.approve(REQUEST))
            .isEqualTo(new PaymentGateway.Unknown());
    }

    @Test
    void responseTimeoutIsUnknownWithoutAnotherApproval() {
        holdResponse = true;

        assertThat(adapter.approve(REQUEST)).isInstanceOf(PaymentGateway.Unknown.class);
        assertThat(calls).hasValue(1);
    }

    private record CapturedRequest(
        String method, String path, String authorization, String contentType, JsonNode body
    ) {
    }
}
