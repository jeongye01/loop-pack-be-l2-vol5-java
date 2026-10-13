package com.loopers.product.infrastructure;

import com.loopers.product.domain.Product;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

interface ProductJpaRepository extends JpaRepository<Product, Long> {

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select p from Product p where p.id = :id")
    Optional<Product> findForOrder(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select p from Product p where p.id = :id")
    Optional<Product> findForLike(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select p from Product p where p.id = :id")
    Optional<Product> findForStock(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id = :id")
    Optional<Product> findForWrite(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.brandId = :brandId order by p.id asc")
    List<Product> findAllForBrandDelete(@Param("brandId") Long brandId);

    List<Product> findAllByBrandId(Long brandId);

    List<Product> findAllByBrandIdAndName(Long brandId, String name);

    List<Product> findAllByBrandId(Long brandId, Pageable pageable);

    long countByBrandId(Long brandId);

    @Query("""
        select count(p) from Product p
        where p.deletedAt is null
          and (:brandId is null or p.brandId = :brandId)
        """)
    long countCustomerProducts(@Param("brandId") Long brandId);

    @Query("""
        select p from Product p
        where p.deletedAt is null
          and (:brandId is null or p.brandId = :brandId)
        order by p.createdAt desc, p.id asc
        """)
    List<Product> findCustomerProductsByLatest(
        @Param("brandId") Long brandId,
        Pageable pageable
    );

    @Query("""
        select p from Product p
        where p.deletedAt is null
          and (:brandId is null or p.brandId = :brandId)
        order by p.price asc, p.id asc
        """)
    List<Product> findCustomerProductsByPrice(
        @Param("brandId") Long brandId,
        Pageable pageable
    );

    @Query(value = """
        select p.* from product p
        left join (
            select product_id, count(*) as like_count
            from product_like
            group by product_id
        ) l on l.product_id = p.id
        where p.deleted_at is null
          and (:brandId is null or p.brand_id = :brandId)
        order by coalesce(l.like_count, 0) desc, p.id asc
        """, nativeQuery = true)
    List<Product> findCustomerProductsByLikes(
        @Param("brandId") Long brandId,
        Pageable pageable
    );

}
