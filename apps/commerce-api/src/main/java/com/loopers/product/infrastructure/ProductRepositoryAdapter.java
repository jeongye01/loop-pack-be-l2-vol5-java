package com.loopers.product.infrastructure;

import com.loopers.product.domain.Product;
import com.loopers.product.domain.ProductRepository;
import com.loopers.product.domain.ProductSort;
import jakarta.persistence.EntityManager;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.DatabaseConstraintErrors;
import com.loopers.support.error.ErrorCode;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class ProductRepositoryAdapter implements ProductRepository {

    private final ProductJpaRepository jpaRepository;
    private final EntityManager entityManager;

    public ProductRepositoryAdapter(
        ProductJpaRepository jpaRepository,
        EntityManager entityManager
    ) {
        this.jpaRepository = jpaRepository;
        this.entityManager = entityManager;
    }

    @Override
    public Product save(Product product) {
        try {
            Product saved;
            if (product.getId() == 0L) {
                entityManager.persist(product);
                saved = product;
            } else {
                saved = jpaRepository.save(product);
            }
            entityManager.flush();
            return saved;
        } catch (RuntimeException failure) {
            if (DatabaseConstraintErrors.isDuplicateKey(failure, "uk_product_active_name")) {
                throw new CoreException(ErrorCode.DUPLICATE_PRODUCT_NAME);
            }
            throw failure;
        }
    }

    @Override
    public Optional<Product> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public Optional<Product> findForOrder(Long id) {
        return jpaRepository.findForOrder(id);
    }

    @Override
    public Optional<Product> findForLike(Long id) {
        return jpaRepository.findForLike(id);
    }

    @Override
    public Optional<Product> findForStock(Long id) {
        return jpaRepository.findForStock(id);
    }

    @Override
    public Optional<Product> findForWrite(Long id) {
        return jpaRepository.findForWrite(id);
    }

    @Override
    public List<Product> findAllForBrandDelete(Long brandId) {
        return jpaRepository.findAllForBrandDelete(brandId);
    }

    @Override
    public List<Product> findAllByBrandId(Long brandId) {
        return jpaRepository.findAllByBrandId(brandId);
    }

    @Override
    public List<Product> findAllByBrandIdAndName(Long brandId, String name) {
        return jpaRepository.findAllByBrandIdAndName(brandId, name);
    }

    @Override
    public List<Product> findAll(Long brandId, int page, int size) {
        Sort sort = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id"));
        PageRequest pageRequest = PageRequest.of(page, size, sort);
        if (brandId == null) {
            return jpaRepository.findAll(pageRequest).getContent();
        }
        return jpaRepository.findAllByBrandId(brandId, pageRequest);
    }

    @Override
    public List<Product> findCustomerProducts(
        Long brandId,
        ProductSort sort,
        int page,
        int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        return switch (sort) {
            case LATEST -> jpaRepository.findCustomerProductsByLatest(brandId, pageable);
            case PRICE_ASC -> jpaRepository.findCustomerProductsByPrice(brandId, pageable);
            case LIKES_DESC -> findCustomerProductsByLikes(brandId, pageable);
        };
    }

    private List<Product> findCustomerProductsByLikes(Long brandId, Pageable pageable) {
        if (brandId == null) {
            return findUnfilteredProductsByLikes(pageable);
        }
        List<Product> liked = jpaRepository.findLikedCustomerProducts(
            brandId, pageable.getOffset(), pageable.getPageSize()
        );
        int remaining = pageable.getPageSize() - liked.size();
        if (remaining == 0) {
            return liked;
        }

        // A partial positive page reaches the zero-like segment at its first row.
        // An empty positive page needs the filtered positive count to translate the offset.
        long zeroOffset = 0;
        if (liked.isEmpty() && pageable.getOffset() > 0) {
            zeroOffset = Math.max(0, pageable.getOffset() - jpaRepository.countLikedCustomerProducts(brandId));
        }
        List<Product> result = new ArrayList<>(liked);
        result.addAll(jpaRepository.findUnlikedCustomerProducts(brandId, zeroOffset, remaining));
        return result;
    }

    private List<Product> findUnfilteredProductsByLikes(Pageable pageable) {
        int pageSize = pageable.getPageSize();
        long remainingSkip = pageable.getOffset();
        long candidateOffset = 0;
        int batchSize = (int) Math.min(1024L, remainingSkip + pageSize);
        List<Long> selectedIds = new ArrayList<>();

        while (selectedIds.size() < pageSize) {
            List<ProductJpaRepository.RankedCandidate> candidates =
                jpaRepository.findRankedCandidates(candidateOffset, batchSize);
            for (ProductJpaRepository.RankedCandidate candidate : candidates) {
                if (candidate.getEligibleProductId() == null) {
                    continue;
                }
                if (remainingSkip > 0) {
                    remainingSkip--;
                } else {
                    selectedIds.add(candidate.getEligibleProductId());
                    if (selectedIds.size() == pageSize) {
                        break;
                    }
                }
            }
            candidateOffset += candidates.size();
            if (selectedIds.size() == pageSize || candidates.size() < batchSize) {
                break;
            }
            batchSize = Math.min(1024, batchSize * 2);
        }

        List<Product> result = new ArrayList<>();
        if (!selectedIds.isEmpty()) {
            Map<Long, Product> products = jpaRepository.findAllById(selectedIds).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
            for (Long id : selectedIds) {
                result.add(products.get(id));
            }
        }
        if (result.size() < pageSize) {
            result.addAll(jpaRepository.findUnlikedCustomerProducts(
                null, remainingSkip, pageSize - result.size()
            ));
        }
        return result;
    }

    @Override
    public long countAll(Long brandId) {
        if (brandId == null) {
            return jpaRepository.count();
        }
        return jpaRepository.countByBrandId(brandId);
    }

    @Override
    public long countCustomerProducts(Long brandId) {
        return jpaRepository.countCustomerProducts(brandId);
    }
}
