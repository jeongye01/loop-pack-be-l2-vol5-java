package com.loopers.order.application;

import com.loopers.brand.domain.Brand;
import com.loopers.order.domain.Order;
import com.loopers.order.domain.OrderStatus;
import com.loopers.product.domain.Product;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorCode;
import com.loopers.support.fixture.CommerceFixture;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.user.domain.User;
import com.loopers.utils.DatabaseCleanUp;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest
@Import(MySqlTestContainersConfig.class)
class OrderConcurrencyTest {

    private static final int REQUESTS = 8;

    @Autowired private OrderUseCase useCase;
    @Autowired private EntityManager entityManager;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private DatabaseCleanUp databaseCleanUp;

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

    @DisplayName("[R-ORDER-11, R-ORDER-12] 재고 5개에 서로 다른 주문 8건을 확정하면 성공 5·재고 부족 3이고 DB 결과가 일치한다.")
    @Test
    void preservesStockAndOrdersUnderConcurrentConfirmation() throws Exception {
        Brand brand = fixture.brand("Nike");
        Product product = fixture.product(brand, "Air", 1_000L, 5);
        List<Purchase> purchases = new ArrayList<>();
        for (int index = 0; index < REQUESTS; index++) {
            User buyer = fixture.userWithPoint(1_000L);
            Order order = fixture.draftOrder(buyer, fixture.item(product, 1));
            purchases.add(new Purchase(order.getId(), buyer.getId()));
        }

        CountDownLatch ready = new CountDownLatch(REQUESTS);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService workers = Executors.newFixedThreadPool(REQUESTS);
        List<Outcome> outcomes = new ArrayList<>();

        try {
            List<Future<Outcome>> futures = new ArrayList<>();
            for (Purchase purchase : purchases) {
                futures.add(workers.submit(() -> confirmAtStart(purchase, ready, start)));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (Future<Outcome> future : futures) {
                outcomes.add(future.get(20, TimeUnit.SECONDS));
            }
        } finally {
            start.countDown();
            workers.shutdownNow();
            assertThat(workers.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }

        long successes = outcomes.stream().filter(Outcome::success).count();
        long stockRejections = outcomes.stream()
            .filter(result -> result.rejection() == ErrorCode.INSUFFICIENT_STOCK).count();
        List<Outcome> unexpected = outcomes.stream()
            .filter(result -> !result.success() && result.rejection() != ErrorCode.INSUFFICIENT_STOCK)
            .toList();

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Product savedProduct = entityManager.find(Product.class, product.getId());
            long confirmed = outcomes.stream()
                .map(result -> entityManager.find(Order.class, result.orderId()))
                .filter(order -> order.getStatus() == OrderStatus.CONFIRMED)
                .count();
            assertAll(
                () -> assertThat(outcomes).hasSize(REQUESTS),
                () -> assertThat(successes).isEqualTo(5),
                () -> assertThat(stockRejections).isEqualTo(3),
                () -> assertThat(unexpected).isEmpty(),
                () -> assertThat(successes + stockRejections + unexpected.size()).isEqualTo(REQUESTS),
                () -> assertThat(savedProduct.getStock().quantity()).isZero(),
                () -> assertThat(5 - successes).isEqualTo((long) savedProduct.getStock().quantity()),
                () -> assertThat(confirmed).isEqualTo(successes)
            );
            for (Outcome outcome : outcomes) {
                Order savedOrder = entityManager.find(Order.class, outcome.orderId());
                User savedBuyer = entityManager.find(User.class, outcome.buyerId());
                if (outcome.success()) {
                    assertAll(
                        () -> assertThat(savedOrder.getStatus()).isEqualTo(OrderStatus.CONFIRMED),
                        () -> assertThat(savedOrder.getPaymentResult().amount()).isEqualTo(1_000L),
                        () -> assertThat(savedBuyer.getPoint().balance()).isZero()
                    );
                } else if (outcome.rejection() == ErrorCode.INSUFFICIENT_STOCK) {
                    assertAll(
                        () -> assertThat(savedOrder.getStatus()).isEqualTo(OrderStatus.DRAFT),
                        () -> assertThat(savedOrder.getPaymentResult()).isNull(),
                        () -> assertThat(savedBuyer.getPoint().balance()).isEqualTo(1_000L)
                    );
                }
            }
        });
    }

    private Outcome confirmAtStart(Purchase purchase, CountDownLatch ready, CountDownLatch start) {
        ready.countDown();
        try {
            if (!start.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("timed out waiting to start confirmation");
            }
            useCase.confirm(purchase.buyerId(), purchase.orderId());
            return new Outcome(purchase.orderId(), purchase.buyerId(), true, null, null);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return new Outcome(purchase.orderId(), purchase.buyerId(), false, null, exception);
        } catch (CoreException exception) {
            return new Outcome(purchase.orderId(), purchase.buyerId(), false, exception.getErrorCode(), null);
        } catch (RuntimeException exception) {
            return new Outcome(purchase.orderId(), purchase.buyerId(), false, null, exception);
        }
    }

    private record Purchase(Long orderId, Long buyerId) {
    }

    private record Outcome(Long orderId, Long buyerId, boolean success, ErrorCode rejection, Exception technical) {
    }
}
