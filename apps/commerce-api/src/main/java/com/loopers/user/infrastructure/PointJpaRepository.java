package com.loopers.user.infrastructure;

import com.loopers.user.domain.Point;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import java.util.Optional;

interface PointJpaRepository extends JpaRepository<Point, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Point> findForOrderByUserId(Long userId);

    Optional<Point> findByUserId(Long userId);
}
