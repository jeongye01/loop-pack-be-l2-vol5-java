package com.loopers.order.application;

import com.loopers.order.domain.Order;
import com.loopers.order.domain.OrderRepository;
import com.loopers.product.domain.ProductRepository;
import com.loopers.user.domain.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class OrderUseCase {

    public OrderUseCase(
        OrderRepository orderRepository,
        ProductRepository productRepository,
        UserRepository userRepository
    ) {
    }

    public Order create(Long buyerId, List<ItemCommand> items) {
        throw new UnsupportedOperationException("Not implemented");
    }

    public Order confirm(Long buyerId, Long orderId) {
        throw new UnsupportedOperationException("Not implemented");
    }

    public List<Order> findMine(Long buyerId, int page, int size) {
        throw new UnsupportedOperationException("Not implemented");
    }

    public Order findMine(Long buyerId, Long orderId) {
        throw new UnsupportedOperationException("Not implemented");
    }

    public List<Order> findForAdmin(Long buyerId, int page, int size) {
        throw new UnsupportedOperationException("Not implemented");
    }

    public Order findForAdmin(Long orderId) {
        throw new UnsupportedOperationException("Not implemented");
    }

    public record ItemCommand(Long productId, int quantity) {
    }
}
