package com.loopers.brand.application;

import com.loopers.brand.domain.Brand;
import com.loopers.product.domain.Product;
import com.loopers.product.infrastructure.ProductRepositoryAdapter;
import com.loopers.support.fixture.CommerceFixture;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest
@Import(MySqlTestContainersConfig.class)
class BrandRemovalTransactionTest {

    @Autowired private BrandUseCase useCase;
    @Autowired private EntityManager entityManager;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private DatabaseCleanUp databaseCleanUp;
    @MockitoSpyBean private ProductRepositoryAdapter productRepository;

    private CommerceFixture fixture;

    @BeforeEach
    void setUp() {
        fixture = new CommerceFixture(entityManager, transactionManager);
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        fixture.truncateRemainingTables();
    }

    @DisplayName("[R-ADMIN-16] 연결 상품 저장 중 실패하면 브랜드와 상품 변경을 모두 되돌린다.")
    @Test
    void rollsBackBrandAndProductsAfterFlushedChange() {
        Brand brand = fixture.brand("Nike");
        Product first = fixture.product(brand, "Air", 1_000L, 0);
        Product second = fixture.product(brand, "Dunk", 2_000L, 3);
        AtomicInteger saveCount = new AtomicInteger();
        AtomicBoolean flushed = new AtomicBoolean();
        doAnswer(invocation -> {
            if (saveCount.incrementAndGet() == 2) {
                throw new IllegalStateException("injected product save failure");
            }
            Object saved = invocation.callRealMethod();
            entityManager.flush();
            assertThat(entityManager.createNativeQuery("select deleted_at from product where id = :id")
                .setParameter("id", first.getId())
                .getSingleResult()).isNotNull();
            flushed.set(true);
            return saved;
        }).when(productRepository).save(any(Product.class));

        IllegalStateException failure = assertThrows(IllegalStateException.class,
            () -> useCase.delete(brand.getId()));

        assertAll(
            () -> assertThat(failure).hasMessage("injected product save failure"),
            () -> assertThat(flushed).isTrue(),
            () -> assertThat(saveCount).hasValue(2)
        );
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> assertAll(
            () -> assertThat(entityManager.find(Brand.class, brand.getId()).isDeleted()).isFalse(),
            () -> assertThat(entityManager.find(Product.class, first.getId()).isDeleted()).isFalse(),
            () -> assertThat(entityManager.find(Product.class, second.getId()).isDeleted()).isFalse()
        ));
    }
}
