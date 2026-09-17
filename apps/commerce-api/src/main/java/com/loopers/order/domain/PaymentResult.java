package com.loopers.order.domain;

import java.time.ZonedDateTime;

public record PaymentResult(Long amount, ZonedDateTime paidAt) {
}
