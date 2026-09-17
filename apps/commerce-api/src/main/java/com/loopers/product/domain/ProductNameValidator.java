package com.loopers.product.domain;

public class ProductNameValidator {

    private final ProductRepository productRepository;

    public ProductNameValidator(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public void validateNotDuplicated(Long brandId, String name) {
    }

    public void validateNotDuplicated(Long brandId, String name, Long excludeProductId) {
    }
}
