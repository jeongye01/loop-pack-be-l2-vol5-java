package com.loopers.order.domain;

import com.loopers.product.domain.Product;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorCode;

public record OrderItem(Long productId, String productName, int quantity, long unitPrice) {

    public OrderItem {
        if (quantity <= 0) {
            throw new CoreException(ErrorCode.INVALID_ORDER_QUANTITY);
        }
    }

    public static OrderItem of(Product product, int quantity) {
        return new OrderItem(product.getId(), product.getName(), quantity, product.getPrice());
    }

    public long amount() {
        return unitPrice * quantity;
    }

    public OrderItem withQuantity(int quantity) {
        return new OrderItem(productId, productName, quantity, unitPrice);
    }
}
