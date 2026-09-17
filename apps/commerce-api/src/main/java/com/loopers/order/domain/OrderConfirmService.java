package com.loopers.order.domain;

import com.loopers.product.domain.Product;
import com.loopers.user.domain.User;

import java.time.ZonedDateTime;
import java.util.List;

public class OrderConfirmService {

    public void confirm(Long requesterId, Order order, List<Product> products, User buyer, ZonedDateTime paidAt) {
    }
}
