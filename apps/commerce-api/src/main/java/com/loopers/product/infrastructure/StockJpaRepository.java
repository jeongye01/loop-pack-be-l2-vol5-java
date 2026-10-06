package com.loopers.product.infrastructure;

import com.loopers.product.domain.Stock;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

interface StockJpaRepository extends JpaRepository<Stock, Long> {
    Optional<Stock> findByProductId(Long productId);
}
