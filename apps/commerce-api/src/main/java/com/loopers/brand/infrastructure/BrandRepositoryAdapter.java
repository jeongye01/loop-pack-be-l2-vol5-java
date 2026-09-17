package com.loopers.brand.infrastructure;

import com.loopers.brand.domain.Brand;
import com.loopers.brand.domain.BrandRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class BrandRepositoryAdapter implements BrandRepository {

    public BrandRepositoryAdapter(BrandJpaRepository jpaRepository) {
    }

    @Override
    public Brand save(Brand brand) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public Optional<Brand> findById(Long id) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public List<Brand> findAllByName(String name) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public List<Brand> findAll(int page, int size) {
        throw new UnsupportedOperationException("Not implemented");
    }
}
