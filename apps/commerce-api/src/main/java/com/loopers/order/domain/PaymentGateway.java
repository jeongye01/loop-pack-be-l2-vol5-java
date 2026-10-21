package com.loopers.order.domain;

import java.time.OffsetDateTime;

public interface PaymentGateway {

    ApprovalResult approve(ApprovalRequest request);

    default ApprovalResult findByPaymentKey(String paymentKey) {
        throw new UnsupportedOperationException("Payment lookup is not implemented");
    }

    record ApprovalRequest(String paymentKey, String providerOrderId, long amount, String idempotencyKey) {
    }

    sealed interface ApprovalResult permits Approved, ProviderError, Unknown {
    }

    record Approved(
        String paymentKey,
        String providerOrderId,
        long amount,
        OffsetDateTime paidAt,
        String rawResponse
    ) implements ApprovalResult {
        public Approved(String paymentKey, String providerOrderId, long amount, OffsetDateTime paidAt) {
            this(paymentKey, providerOrderId, amount, paidAt, null);
        }
    }

    // PG 오류 관찰이며 내부 결제 FAILED를 의미하지 않는다.
    record ProviderError(int httpStatus, String code, String message, String rawResponse) implements ApprovalResult {
        public ProviderError(int httpStatus, String code, String message) {
            this(httpStatus, code, message, null);
        }
    }

    record Unknown(String rawResponse) implements ApprovalResult {
        public Unknown() {
            this(null);
        }
    }
}
