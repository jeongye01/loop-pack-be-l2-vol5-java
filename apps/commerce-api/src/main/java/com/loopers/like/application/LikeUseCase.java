package com.loopers.like.application;

import com.loopers.like.domain.LikeRepository;
import com.loopers.product.domain.Product;
import com.loopers.product.domain.ProductRepository;
import com.loopers.user.domain.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class LikeUseCase {

    public LikeUseCase(
        LikeRepository likeRepository,
        ProductRepository productRepository,
        UserRepository userRepository
    ) {
    }

    public boolean register(Long userId, Long productId) {
        throw new UnsupportedOperationException("Not implemented");
    }

    public void cancel(Long userId, Long productId) {
        throw new UnsupportedOperationException("Not implemented");
    }

    public List<Product> findMine(Long userId, int page, int size) {
        throw new UnsupportedOperationException("Not implemented");
    }

    public long count(Long productId) {
        throw new UnsupportedOperationException("Not implemented");
    }
}
