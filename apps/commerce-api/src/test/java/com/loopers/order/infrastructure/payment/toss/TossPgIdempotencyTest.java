package com.loopers.order.infrastructure.payment.toss;

import com.loopers.order.domain.PaymentGateway;
import com.loopers.order.domain.PaymentGateway.ApprovalRequest;
import com.loopers.order.domain.PaymentGateway.Approved;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PG 멱등 응답 계약")
class TossPgIdempotencyTest {

    @Test
    @DisplayName("같은 멱등키로 재요청하고 PG가 재사용한 성공 응답을 변환한다")
    void reusesIdempotencyKeyAndConvertsRepeatedSuccess() {
        var request = new ApprovalRequest("test-payment-42", "LP-ORD-0000000042", 7000, "test-attempt-42");
        var approvedAt = OffsetDateTime.parse("2026-10-21T15:00:00+09:00");

        try (var pg = new TossPgFixture()) {
            pg.approve(request, approvedAt);
            PaymentGateway gateway = new TossPaymentAdapter(TossHttpClientFactory.create(
                pg.baseUrl(), TossPgFixture.SECRET_KEY, Duration.ofSeconds(1), Duration.ofSeconds(1)));

            var first = gateway.approve(request);
            var repeated = gateway.approve(request);

            assertThat(first).isEqualTo(new Approved(
                request.paymentKey(), request.providerOrderId(), request.amount(), approvedAt));
            assertThat(pg.approvalRequests()).hasSize(2);
            assertThat(pg.unmatchedRequests()).isEmpty();
            assertThat(pg.approvalRequests()).allSatisfy(httpRequest -> {
                assertThat(httpRequest.getHeader("Idempotency-Key")).isEqualTo(request.idempotencyKey());
            });
            assertThat(repeated).isEqualTo(first);
        }
    }
}
