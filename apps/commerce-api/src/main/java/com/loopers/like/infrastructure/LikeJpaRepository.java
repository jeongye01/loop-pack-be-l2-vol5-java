package com.loopers.like.infrastructure;

import com.loopers.like.domain.Like;
import org.springframework.data.jpa.repository.JpaRepository;

interface LikeJpaRepository extends JpaRepository<Like, Long> {
}
