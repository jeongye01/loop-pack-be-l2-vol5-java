package com.loopers.order.domain;

import java.time.OffsetDateTime;

public interface PaymentGateway {

    ApprovalResult approve(ApprovalRequest request);

    record ApprovalRequest(String paymentKey, String providerOrderId, long amount, String idempotencyKey) {
    }

    sealed interface ApprovalResult permits Approved, ProviderError, Unknown {
    }

    record Approved(
        String paymentKey,
        String providerOrderId,
        long amount,
        OffsetDateTime paidAt
    ) implements ApprovalResult {
    }

    // PG 오류 관찰이며 내부 결제 FAILED를 의미하지 않는다.
    record ProviderError(int httpStatus, String code, String message) implements ApprovalResult {
    }

    record Unknown() implements ApprovalResult {
    }
}
