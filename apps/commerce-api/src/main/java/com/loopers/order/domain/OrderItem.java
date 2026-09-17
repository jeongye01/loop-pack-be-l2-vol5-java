package com.loopers.order.domain;

import com.loopers.product.domain.Product;

public record OrderItem(Long productId, String productName, int quantity, long unitPrice) {

    public static OrderItem of(Product product, int quantity) {
        return new OrderItem(null, null, quantity, 0);
    }

    public long amount() {
        return 0;
    }

    public OrderItem withQuantity(int quantity) {
        return this;
    }
}
