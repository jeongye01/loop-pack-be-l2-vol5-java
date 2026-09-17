package com.loopers.brand.application;

import com.loopers.brand.domain.Brand;
import com.loopers.brand.domain.BrandRepository;
import com.loopers.product.domain.ProductRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class BrandUseCase {

    public BrandUseCase(BrandRepository brandRepository, ProductRepository productRepository) {
    }

    public Brand create(String name) {
        throw new UnsupportedOperationException("Not implemented");
    }

    public Brand update(Long brandId, String name) {
        throw new UnsupportedOperationException("Not implemented");
    }

    public Brand find(Long brandId) {
        throw new UnsupportedOperationException("Not implemented");
    }

    public void delete(Long brandId) {
        throw new UnsupportedOperationException("Not implemented");
    }

    public List<Brand> findAll(int page, int size) {
        throw new UnsupportedOperationException("Not implemented");
    }
}
