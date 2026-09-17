package com.loopers.brand.domain;

import com.loopers.product.domain.ProductRepository;

public class BrandDeletionValidator {

    private final ProductRepository productRepository;

    public BrandDeletionValidator(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public void validateDeletable(Brand brand) {
    }
}
