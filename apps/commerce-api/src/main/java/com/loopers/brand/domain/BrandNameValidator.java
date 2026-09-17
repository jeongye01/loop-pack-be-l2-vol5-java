package com.loopers.brand.domain;

public class BrandNameValidator {

    private final BrandRepository brandRepository;

    public BrandNameValidator(BrandRepository brandRepository) {
        this.brandRepository = brandRepository;
    }

    public void validateNotDuplicated(String name) {
    }

    public void validateNotDuplicated(String name, Long excludeBrandId) {
    }
}
