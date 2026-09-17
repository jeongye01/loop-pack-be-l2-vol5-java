package com.loopers.product.domain;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorCode;

public record Stock(int quantity) {

    public Stock {
        if (quantity < 0) {
            throw new CoreException(ErrorCode.INVALID_STOCK_QUANTITY);
        }
    }

    public Stock decrease(int amount) {
        if (amount <= 0) {
            throw new CoreException(ErrorCode.INTERNAL_ERROR);
        }
        if (amount > quantity) {
            throw new CoreException(ErrorCode.INSUFFICIENT_STOCK);
        }
        return new Stock(quantity - amount);
    }
}
