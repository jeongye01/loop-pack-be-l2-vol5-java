package com.loopers.order.infrastructure;

import com.loopers.order.domain.Order;
import org.springframework.data.jpa.repository.JpaRepository;

interface OrderJpaRepository extends JpaRepository<Order, Long> {
}
