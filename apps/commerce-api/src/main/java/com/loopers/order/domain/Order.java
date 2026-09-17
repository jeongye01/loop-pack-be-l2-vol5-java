package com.loopers.order.domain;

import com.loopers.domain.BaseEntity;

import java.time.ZonedDateTime;
import java.util.List;

public class Order extends BaseEntity {

    private Long buyerId;
    private List<OrderItem> items;

    public Order(Long buyerId, List<OrderItem> items) {
        this.buyerId = buyerId;
        this.items = items;
    }

    public Long getBuyerId() {
        return buyerId;
    }

    public List<OrderItem> getItems() {
        return items;
    }

    public long getTotalAmount() {
        return 0;
    }

    public OrderStatus getStatus() {
        return null;
    }

    public PaymentResult getPaymentResult() {
        return null;
    }

    public boolean isOwnedBy(Long userId) {
        return false;
    }

    public void changeItemQuantity(Long requesterId, Long productId, int quantity) {
    }

    public void confirm(long paymentAmount, ZonedDateTime paidAt) {
    }
}
