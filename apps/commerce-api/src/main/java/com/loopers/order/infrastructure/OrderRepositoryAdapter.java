package com.loopers.order.infrastructure;

import com.loopers.order.domain.Order;
import com.loopers.order.domain.OrderRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class OrderRepositoryAdapter implements OrderRepository {

    public OrderRepositoryAdapter(OrderJpaRepository jpaRepository) {
    }

    @Override
    public Order save(Order order) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public Optional<Order> findById(Long id) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public List<Order> findAllByBuyerId(Long buyerId, int page, int size) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public List<Order> findAll(Long buyerId, int page, int size) {
        throw new UnsupportedOperationException("Not implemented");
    }
}
