package com.loopers.like.infrastructure;

import com.loopers.like.domain.Like;
import com.loopers.like.domain.LikeRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class LikeRepositoryAdapter implements LikeRepository {

    public LikeRepositoryAdapter(LikeJpaRepository jpaRepository) {
    }

    @Override
    public Like save(Like like) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public Optional<Like> findByUserIdAndProductId(Long userId, Long productId) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public List<Like> findAllByUserId(Long userId, int page, int size) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public long countByProductId(Long productId) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public void delete(Like like) {
        throw new UnsupportedOperationException("Not implemented");
    }
}
