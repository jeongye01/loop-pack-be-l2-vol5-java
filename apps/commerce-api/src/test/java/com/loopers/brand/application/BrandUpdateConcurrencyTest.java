package com.loopers.brand.application;

import com.loopers.brand.domain.Brand;
import com.loopers.brand.infrastructure.BrandRepositoryAdapter;
import com.loopers.product.domain.Product;
import com.loopers.support.fixture.CommerceFixture;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import jakarta.persistence.EntityManager;
import jakarta.persistence.OptimisticLockException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest
@Import(MySqlTestContainersConfig.class)
class BrandUpdateConcurrencyTest {

    @Autowired private BrandUseCase useCase;
    @Autowired private EntityManager entityManager;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private DatabaseCleanUp databaseCleanUp;
    @MockitoSpyBean private BrandRepositoryAdapter brandRepository;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        new CommerceFixture(entityManager, transactionManager).truncateRemainingTables();
    }

    @DisplayName("[R-ADMIN-01] 같은 브랜드 버전을 읽은 두 수정 중 하나만 성공하고 충돌한 수정은 재시도하지 않는다.")
    @Test
    void detectsConcurrentUpdatesWithoutRetry() throws Exception {
        Brand brand = new CommerceFixture(entityManager, transactionManager).brand("Nike");
        CountDownLatch bothRead = new CountDownLatch(2);
        CountDownLatch allowUpdate = new CountDownLatch(1);
        doAnswer(invocation -> {
            Object result = invocation.callRealMethod();
            bothRead.countDown();
            await(allowUpdate);
            return result;
        }).when(brandRepository).findById(brand.getId());
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<UpdateOutcome> first = workers.submit(() -> update(brand.getId(), "Nike A"));
            Future<UpdateOutcome> second = workers.submit(() -> update(brand.getId(), "Nike B"));
            assertThat(bothRead.await(10, TimeUnit.SECONDS)).isTrue();
            allowUpdate.countDown();
            List<UpdateOutcome> outcomes = List.of(
                first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
            assertThat(outcomes).filteredOn(UpdateOutcome::success).hasSize(1);
            assertThat(outcomes).filteredOn(result -> !result.success()).hasSize(1);
            String winningName = outcomes.stream().filter(UpdateOutcome::success)
                .map(UpdateOutcome::name).findFirst().orElseThrow();
            verify(brandRepository, times(2)).findById(brand.getId());
            verify(brandRepository, times(2)).save(any(Brand.class));
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                Brand saved = entityManager.find(Brand.class, brand.getId());
                assertThat(saved.getName()).isEqualTo(winningName);
                assertThat(saved.isDeleted()).isFalse();
                assertThat(((Number) entityManager.createNativeQuery("select version from brand where id = :id")
                    .setParameter("id", brand.getId()).getSingleResult()).longValue()).isEqualTo(1L);
            });
        } finally {
            allowUpdate.countDown();
            workers.shutdownNow();
            assertThat(workers.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @DisplayName("[R-ADMIN-13, R-ADMIN-16] 수정 조회 이후 브랜드가 삭제되면 오래된 수정은 충돌하고 삭제 상태를 덮어쓰지 않는다.")
    @Test
    void rejectsStaleUpdateAfterDeletion() throws Exception {
        CommerceFixture fixture = new CommerceFixture(entityManager, transactionManager);
        Brand brand = fixture.brand("Nike");
        Product product = fixture.product(brand, "Air", 1_000L, 5);
        CountDownLatch read = new CountDownLatch(1);
        CountDownLatch allowUpdate = new CountDownLatch(1);
        doAnswer(invocation -> {
            Object result = invocation.callRealMethod();
            read.countDown();
            await(allowUpdate);
            return result;
        }).when(brandRepository).findById(brand.getId());
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<UpdateOutcome> update = workers.submit(() -> update(brand.getId(), "Nike A"));
            assertThat(read.await(10, TimeUnit.SECONDS)).isTrue();
            Future<?> deletion = workers.submit(() -> useCase.delete(brand.getId()));
            deletion.get(10, TimeUnit.SECONDS);
            allowUpdate.countDown();
            assertThat(update.get(10, TimeUnit.SECONDS).success()).isFalse();
            verify(brandRepository, times(1)).findById(brand.getId());
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                Brand saved = entityManager.find(Brand.class, brand.getId());
                assertThat(saved.getName()).isEqualTo("Nike");
                assertThat(saved.isDeleted()).isTrue();
                assertThat(entityManager.find(Product.class, product.getId()).isDeleted()).isTrue();
            });
        } finally {
            allowUpdate.countDown();
            workers.shutdownNow();
            assertThat(workers.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private UpdateOutcome update(Long brandId, String name) {
        try {
            useCase.update(brandId, name);
            return new UpdateOutcome(true, name);
        } catch (OptimisticLockException | OptimisticLockingFailureException exception) {
            return new UpdateOutcome(false, name);
        }
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("timed out waiting to update brand");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while waiting to update brand", exception);
        }
    }

    private record UpdateOutcome(boolean success, String name) {
    }
}
