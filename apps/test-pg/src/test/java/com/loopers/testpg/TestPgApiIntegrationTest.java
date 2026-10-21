package com.loopers.testpg;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Base64;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("테스트 PG HTTP 계약")
class TestPgApiIntegrationTest {

    private static final String CONFIRM_PATH = "/v1/payments/confirm";
    private static final String PAYMENTS_PATH = "/api/v1/payments";
    private static final String SECRET_KEY = "local-secret";
    private static final String AUTHORIZATION = "Basic " + Base64.getEncoder()
        .encodeToString((SECRET_KEY + ":").getBytes(StandardCharsets.UTF_8));
    private static final String REQUEST_BODY = """
        {
          "paymentKey": "test-payment-42",
          "orderId": "LP-ORD-0000000042",
          "amount": 7000
        }
        """;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private WireMockServer server;

    @BeforeEach
    void setUp() {
        var store = new TransactionStore();
        server = new WireMockServer(WireMockConfiguration.options()
            .dynamicPort()
            .extensions(new TestPgResponseTransformer(store, objectMapper, SECRET_KEY)));
        server.start();
        server.stubFor(post(urlEqualTo(CONFIRM_PATH))
            .willReturn(aResponse().withStatus(404).withTransformers("local-test-pg-api")));
        server.stubFor(get(urlPathEqualTo(PAYMENTS_PATH))
            .willReturn(aResponse().withStatus(404).withTransformers("local-test-pg-api")));
    }

    @AfterEach
    void tearDown() {
        server.stop();
    }

    @Test
    @DisplayName("정상 결제 확인 요청은 거래를 생성하고 DONE으로 승인한다")
    void createsAndApprovesTransaction() throws Exception {
        HttpResponse<String> response = confirmPayment("test-payment-42", "attempt-42");

        assertThat(response.statusCode()).isEqualTo(200);
        var body = objectMapper.readTree(response.body());
        assertThat(body.get("paymentKey").asText()).isEqualTo("test-payment-42");
        assertThat(body.get("orderId").asText()).isEqualTo("LP-ORD-0000000042");
        assertThat(body.get("totalAmount").asLong()).isEqualTo(7000);
        assertThat(body.get("status").asText()).isEqualTo("DONE");
        assertThat(OffsetDateTime.parse(body.get("approvedAt").asText())).isNotNull();

        HttpResponse<String> transactions = httpClient.send(HttpRequest.newBuilder()
                .uri(URI.create(server.baseUrl() + PAYMENTS_PATH + "?orderId=LP-ORD-0000000042"))
                .header("X-USER-ID", "135135")
                .GET()
                .build(), HttpResponse.BodyHandlers.ofString());
        assertThat(transactions.statusCode()).isEqualTo(200);
        var savedTransactions = objectMapper.readTree(transactions.body());
        assertThat(savedTransactions).hasSize(1);
        assertThat(savedTransactions.get(0).get("paymentKey").asText()).isEqualTo("test-payment-42");
        assertThat(savedTransactions.get(0).get("orderId").asText()).isEqualTo("LP-ORD-0000000042");
        assertThat(savedTransactions.get(0).get("amount").asLong()).isEqualTo(7000);
        assertThat(savedTransactions.get(0).get("status").asText()).isEqualTo("SUCCESS");
    }

    @Test
    @DisplayName("최초 카드 승인 거절은 403으로 응답하고 거래를 생성하지 않는다")
    void rejectsCardPaymentWithoutCreatingTransaction() throws Exception {
        HttpResponse<String> response = confirmPayment("test-payment-rejected", "attempt-rejected");

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(objectMapper.readTree(response.body())).isEqualTo(objectMapper.readTree("""
            {
              "code": "REJECT_CARD_PAYMENT",
              "message": "한도초과 혹은 잔액부족으로 결제에 실패했습니다."
            }
            """));

        HttpResponse<String> transactions = httpClient.send(HttpRequest.newBuilder()
                .uri(URI.create(server.baseUrl() + PAYMENTS_PATH + "?orderId=LP-ORD-0000000042"))
                .header("X-USER-ID", "135135")
                .GET()
                .build(), HttpResponse.BodyHandlers.ofString());
        assertThat(transactions.statusCode()).isEqualTo(200);
        assertThat(objectMapper.readTree(transactions.body())).isEmpty();
    }

    @Test
    @DisplayName("승인 성공 후 같은 멱등키로 재요청하면 최초 응답을 반환하고 거래를 추가 생성하지 않는다")
    void reusesSuccessfulResponseForSameIdempotencyKey() throws Exception {
        HttpResponse<String> first = confirmPayment("test-payment-42", "attempt-42");
        HttpResponse<String> repeated = confirmPayment("test-payment-42", "attempt-42");

        assertThat(first.statusCode()).isEqualTo(200);
        assertThat(repeated.statusCode()).isEqualTo(200);
        assertThat(repeated.body()).isEqualTo(first.body());
        assertThat(transactionsForOrder("LP-ORD-0000000042")).hasSize(1);
    }

    @Test
    @DisplayName("카드 승인 거절 후 같은 멱등키로 재요청하면 최초 거절 응답을 반환한다")
    void reusesRejectedResponseForSameIdempotencyKey() throws Exception {
        HttpResponse<String> first = confirmPayment("test-payment-rejected", "attempt-rejected");
        HttpResponse<String> repeated = confirmPayment("test-payment-rejected", "attempt-rejected");

        assertThat(first.statusCode()).isEqualTo(403);
        assertThat(repeated.statusCode()).isEqualTo(403);
        assertThat(repeated.body()).isEqualTo(first.body());
        assertThat(objectMapper.readTree(first.body()).get("code").asText())
            .isEqualTo("REJECT_CARD_PAYMENT");
        assertThat(transactionsForOrder("LP-ORD-0000000042")).isEmpty();
    }

    @Test
    @DisplayName("PG 내부 500 응답은 FAILED_INTERNAL_SYSTEM_PROCESSING 오류로 반환한다")
    void returnsTossServerErrorResponse() throws Exception {
        HttpResponse<String> response = confirmPayment("test-payment-server-error", "attempt-server-error");

        assertThat(response.statusCode()).isEqualTo(500);
        assertThat(objectMapper.readTree(response.body())).isEqualTo(objectMapper.readTree("""
            {
              "code": "FAILED_INTERNAL_SYSTEM_PROCESSING",
              "message": "내부 시스템 처리 작업이 실패했습니다. 잠시 후 다시 시도해주세요."
            }
            """));
    }

    private JsonNode transactionsForOrder(String orderId) throws Exception {
        HttpResponse<String> response = httpClient.send(HttpRequest.newBuilder()
                .uri(URI.create(server.baseUrl() + PAYMENTS_PATH + "?orderId=" + orderId))
                .header("X-USER-ID", "135135")
                .GET()
                .build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        return objectMapper.readTree(response.body());
    }

    private HttpResponse<String> confirmPayment(String paymentKey, String idempotencyKey) throws Exception {
        String body = REQUEST_BODY.replace("test-payment-42", paymentKey);
        return httpClient.send(HttpRequest.newBuilder()
                .uri(URI.create(server.baseUrl() + CONFIRM_PATH))
                .header("Authorization", AUTHORIZATION)
                .header("Idempotency-Key", idempotencyKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build(), HttpResponse.BodyHandlers.ofString());
    }
}
