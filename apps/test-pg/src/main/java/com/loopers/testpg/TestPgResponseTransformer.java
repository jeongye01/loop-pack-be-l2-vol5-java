package com.loopers.testpg;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.extension.ResponseTransformerV2;
import com.github.tomakehurst.wiremock.http.HttpHeader;
import com.github.tomakehurst.wiremock.http.HttpHeaders;
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.http.Response;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TestPgResponseTransformer implements ResponseTransformerV2 {

    private static final String USER_ID = "135135";
    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    private static final String CONFIRM_PATH = "/v1/payments/confirm";
    private static final String PAYMENTS_PATH = "/api/v1/payments";
    private static final String TEST_STATUS_PATH = "/__test/payments/status";
    private static final String REJECTED_PAYMENT_KEY = "test-payment-rejected";
    private static final String SERVER_ERROR_PAYMENT_KEY = "test-payment-server-error";
    private static final String CARD_REJECTION_MESSAGE = "한도초과 혹은 잔액부족으로 결제에 실패했습니다.";
    private static final String INTERNAL_ERROR_MESSAGE = "내부 시스템 처리 작업이 실패했습니다. 잠시 후 다시 시도해주세요.";
    private static final Pattern TRANSACTION_PATH = Pattern.compile("^/api/v1/payments/([^/]+)$");

    private final TransactionStore store;
    private final ObjectMapper objectMapper;
    private final String authorization;

    public TestPgResponseTransformer(TransactionStore store, ObjectMapper objectMapper, String secretKey) {
        this.store = store;
        this.objectMapper = objectMapper;
        this.authorization = "Basic " + Base64.getEncoder().encodeToString(
            (secretKey + ":").getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public Response transform(Response response, ServeEvent serveEvent) {
        Request request = serveEvent.getRequest();
        String path = request.getUrl().split("\\?", 2)[0];
        if (path.equals(CONFIRM_PATH)) {
            return confirm(request, response);
        }
        if (path.equals(TEST_STATUS_PATH)) {
            return updateStatus(request, response);
        }
        if (!path.equals(PAYMENTS_PATH) && !path.startsWith(PAYMENTS_PATH + "/")) {
            return response;
        }
        if (!USER_ID.equals(request.getHeader("X-USER-ID"))) {
            return json(response, 401, objectMapper.createObjectNode()
                .put("code", "INVALID_USER")
                .put("message", "X-USER-ID must be 135135."));
        }

        String method = request.getMethod().getName();
        if ("GET".equals(method) && PAYMENTS_PATH.equals(path)) {
            return findByOrderId(request, response);
        }
        if ("GET".equals(method)) {
            return findByTransactionKey(response, path);
        }
        return json(response, 405, objectMapper.createObjectNode()
            .put("code", "METHOD_NOT_ALLOWED"));
    }

    private Response confirm(Request request, Response response) {
        if (!"POST".equals(request.getMethod().getName())) {
            return json(response, 405, objectMapper.createObjectNode().put("code", "METHOD_NOT_ALLOWED"));
        }
        if (!authorization.equals(request.getHeader("Authorization"))) {
            return json(response, 401, objectMapper.createObjectNode()
                .put("code", "UNAUTHORIZED_REQUEST")
                .put("message", "Invalid authorization."));
        }
        String idempotencyKey = request.getHeader(IDEMPOTENCY_KEY_HEADER);
        if (idempotencyKey != null && idempotencyKey.length() > 300) {
            return json(response, 400, objectMapper.createObjectNode()
                .put("code", "INVALID_IDEMPOTENCY_KEY")
                .put("message", "멱등키는 300자 이하여야 합니다."));
        }
        try {
            JsonNode body = objectMapper.readTree(request.getBodyAsString());
            String paymentKey = text(body, "paymentKey");
            String orderId = text(body, "orderId");
            JsonNode amountNode = body.path("amount");
            if (paymentKey == null || orderId == null || orderId.length() < 6
                || !amountNode.isIntegralNumber() || amountNode.longValue() <= 0) {
                return json(response, 400, objectMapper.createObjectNode()
                    .put("code", "INVALID_REQUEST")
                    .put("message", "Invalid payment confirmation request."));
            }

            TransactionStore.Submission submission;
            if (REJECTED_PAYMENT_KEY.equals(paymentKey)) {
                submission = store.error(idempotencyKey, 403, "REJECT_CARD_PAYMENT", CARD_REJECTION_MESSAGE);
            } else if (SERVER_ERROR_PAYMENT_KEY.equals(paymentKey)) {
                submission = store.error(
                    idempotencyKey, 500, "FAILED_INTERNAL_SYSTEM_PROCESSING", INTERNAL_ERROR_MESSAGE);
            } else {
                submission = store.confirm(idempotencyKey, paymentKey, orderId, amountNode.longValue());
            }
            if (submission.errorCode() != null) {
                return json(response, submission.httpStatus(), objectMapper.createObjectNode()
                    .put("code", submission.errorCode())
                    .put("message", submission.errorMessage()));
            }
            TransactionStore.Transaction transaction = submission.transaction();
            return json(response, 200, objectMapper.createObjectNode()
                .put("paymentKey", submission.paymentKey())
                .put("orderId", transaction.orderId())
                .put("totalAmount", transaction.amount())
                .put("status", "DONE")
                .put("approvedAt", submission.approvedAt().toString()));
        } catch (IOException exception) {
            return json(response, 400, objectMapper.createObjectNode()
                .put("code", "INVALID_REQUEST")
                .put("message", "Invalid JSON."));
        }
    }

    private Response findByTransactionKey(Response response, String path) {
        Matcher matcher = TRANSACTION_PATH.matcher(path);
        if (!matcher.matches()) {
            return json(response, 404, objectMapper.createObjectNode());
        }
        String key = decode(matcher.group(1));
        return store.findByTransactionKey(key)
            .map(transaction -> json(response, 200, objectMapper.valueToTree(transaction)))
            .orElseGet(() -> json(response, 404, objectMapper.createObjectNode()));
    }

    private Response findByOrderId(Request request, Response response) {
        String orderId = queryParameter(request.getUrl(), "orderId");
        if (orderId == null) {
            return json(response, 400, objectMapper.createObjectNode()
                .put("code", "ORDER_ID_REQUIRED"));
        }
        List<JsonNode> transactions = store.findByOrderId(orderId).stream()
            .map(transaction -> (JsonNode) objectMapper.valueToTree(transaction))
            .toList();
        return json(response, 200, objectMapper.valueToTree(transactions));
    }

    private Response updateStatus(Request request, Response response) {
        if (!"POST".equals(request.getMethod().getName())
            || !USER_ID.equals(request.getHeader("X-USER-ID"))) {
            return json(response, 404, objectMapper.createObjectNode());
        }
        try {
            JsonNode body = objectMapper.readTree(request.getBodyAsString());
            String requestedStatus = text(body, "status");
            String transactionKey = text(body, "transactionKey");
            if (requestedStatus == null || transactionKey == null) {
                return json(response, 400, objectMapper.createObjectNode()
                    .put("code", "INVALID_STATUS"));
            }
            TransactionStore.TransactionStatus status = TransactionStore.TransactionStatus.valueOf(requestedStatus);
            return store.updateStatus(transactionKey, status)
                .map(transaction -> json(response, 200, objectMapper.valueToTree(transaction)))
                .orElseGet(() -> json(response, 404, objectMapper.createObjectNode()));
        } catch (IOException | IllegalArgumentException exception) {
            return json(response, 400, objectMapper.createObjectNode()
                .put("code", "INVALID_STATUS"));
        }
    }

    private String queryParameter(String url, String name) {
        int queryStart = url.indexOf('?');
        if (queryStart < 0) {
            return null;
        }
        for (String part : url.substring(queryStart + 1).split("&")) {
            String[] pair = part.split("=", 2);
            if (pair.length == 2 && name.equals(decode(pair[0]))) {
                return decode(pair[1]);
            }
        }
        return null;
    }

    private String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private String text(JsonNode body, String field) {
        JsonNode value = body.path(field);
        return value.isTextual() ? value.textValue() : null;
    }

    private Response json(Response original, int status, JsonNode body) {
        return Response.Builder.like(original)
            .but()
            .status(status)
            .headers(new HttpHeaders(new HttpHeader("Content-Type", "application/json")))
            .body(body.toString())
            .build();
    }

    @Override
    public String getName() {
        return "local-test-pg-api";
    }

    @Override
    public boolean applyGlobally() {
        return false;
    }
}
