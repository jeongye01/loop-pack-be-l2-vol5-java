package com.loopers.product.application;

import com.loopers.brand.domain.BrandRepository;
import com.loopers.like.domain.LikeRepository;
import com.loopers.product.domain.Product;
import com.loopers.product.domain.ProductRepository;
import com.loopers.product.domain.ProductSort;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProductUseCase {

    public ProductUseCase(
        ProductRepository productRepository,
        BrandRepository brandRepository,
        LikeRepository likeRepository
    ) {
    }

    public Product create(Long brandId, String name, long price) {
        throw new UnsupportedOperationException("Not implemented");
    }

    public Product changeStock(Long productId, int quantity) {
        throw new UnsupportedOperationException("Not implemented");
    }

    public Product find(Long productId) {
        throw new UnsupportedOperationException("Not implemented");
    }

    public List<Product> findAll(Long brandId, int page, int size) {
        throw new UnsupportedOperationException("Not implemented");
    }

    public Product update(Long productId, String name, long price, Long brandId) {
        throw new UnsupportedOperationException("Not implemented");
    }

    public void delete(Long productId) {
        throw new UnsupportedOperationException("Not implemented");
    }

    public List<ProductView> findCustomerProducts(
        Long brandId,
        ProductSort sort,
        int page,
        int size
    ) {
        throw new UnsupportedOperationException("Not implemented");
    }

    public record ProductView(Product product, long likeCount) {
    }
}
