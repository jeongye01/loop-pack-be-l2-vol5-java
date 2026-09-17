package com.loopers.product.infrastructure;

import com.loopers.product.domain.Product;
import com.loopers.product.domain.ProductRepository;
import com.loopers.product.domain.ProductSort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class ProductRepositoryAdapter implements ProductRepository {

    public ProductRepositoryAdapter(ProductJpaRepository jpaRepository) {
    }

    @Override
    public Product save(Product product) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public Optional<Product> findById(Long id) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public List<Product> findAllByBrandId(Long brandId) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public List<Product> findAllByBrandIdAndName(Long brandId, String name) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public List<Product> findAll(Long brandId, int page, int size) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public List<Product> findCustomerProducts(
        Long brandId,
        ProductSort sort,
        int page,
        int size
    ) {
        throw new UnsupportedOperationException("Not implemented");
    }
}
