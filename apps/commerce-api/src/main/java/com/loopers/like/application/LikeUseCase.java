package com.loopers.like.application;

import com.loopers.like.domain.Like;
import com.loopers.like.domain.LikeRepository;
import com.loopers.product.domain.Product;
import com.loopers.product.domain.ProductRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorCode;
import com.loopers.user.domain.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class LikeUseCase {

    private final LikeRepository likeRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    public LikeUseCase(
        LikeRepository likeRepository,
        ProductRepository productRepository,
        UserRepository userRepository
    ) {
        this.likeRepository = likeRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public boolean register(Long userId, Long productId) {
        requireUser(userId);
        requireActiveProduct(productId);
        if (likeRepository.findByUserIdAndProductId(userId, productId).isPresent()) {
            return false;
        }
        likeRepository.save(new Like(userId, productId));
        return true;
    }

    @Transactional
    public void cancel(Long userId, Long productId) {
        requireUser(userId);
        likeRepository.findByUserIdAndProductId(userId, productId)
            .ifPresent(like -> {
                like.cancel(userId);
                likeRepository.delete(like);
            });
    }

    @Transactional(readOnly = true)
    public List<Product> findMine(Long userId, int page, int size) {
        requireUser(userId);
        return likeRepository.findAllByUserId(userId, page, size).stream()
            .map(like -> productRepository.findById(like.getProductId())
                .orElseThrow(() -> new CoreException(ErrorCode.PRODUCT_NOT_FOUND)))
            .toList();
    }

    @Transactional(readOnly = true)
    public long count(Long productId) {
        return likeRepository.countByProductId(productId);
    }

    private void requireUser(Long userId) {
        if (userRepository.findById(userId).isEmpty()) {
            throw new CoreException(ErrorCode.USER_NOT_IDENTIFIED);
        }
    }

    private Product requireActiveProduct(Long productId) {
        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new CoreException(ErrorCode.PRODUCT_NOT_FOUND));
        if (product.isDeleted()) {
            throw new CoreException(ErrorCode.PRODUCT_NOT_FOUND);
        }
        return product;
    }
}
