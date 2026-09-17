package com.loopers.order.domain;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorCode;

import java.time.ZonedDateTime;

public record PaymentResult(Long amount, ZonedDateTime paidAt) {

    public PaymentResult {
        if (amount == null || paidAt == null) {
            throw new CoreException(ErrorCode.INTERNAL_ERROR);
        }
    }
}
