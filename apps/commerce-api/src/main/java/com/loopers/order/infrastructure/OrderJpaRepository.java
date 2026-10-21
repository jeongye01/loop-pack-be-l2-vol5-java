package com.loopers.order.infrastructure;

import com.loopers.order.domain.Order;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.List;
import java.util.Optional;

interface OrderJpaRepository extends JpaRepository<Order, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Order> findForConfirmById(Long id);
    Optional<Order> findByPayment_PaymentAttemptKey(String paymentAttemptKey);

    List<Order> findAllByBuyerId(Long buyerId, Pageable pageable);

    long countByBuyerId(Long buyerId);
}
