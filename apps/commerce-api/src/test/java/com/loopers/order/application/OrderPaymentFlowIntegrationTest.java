package com.loopers.order.application;

import com.loopers.order.domain.Order;
import com.loopers.order.domain.OrderItem;
import com.loopers.order.domain.OrderStatus;
import com.loopers.order.domain.Payment;
import com.loopers.order.domain.PaymentGateway;
import com.loopers.order.domain.PaymentMethod;
import com.loopers.order.domain.PaymentStatus;
import com.loopers.product.domain.Product;
import com.loopers.product.domain.Stock;
import com.loopers.support.fixture.TestEntities;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorCode;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.user.domain.Point;
import com.loopers.user.domain.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertThrows;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@Import(MySqlTestContainersConfig.class)
@Transactional
@DisplayName("동기 PG 결제 흐름")
class OrderPaymentFlowIntegrationTest {

    private static final String PROCESSED_ATTEMPT_KEY = "test-attempt-42";
    private static final OffsetDateTime APPROVED_AT = OffsetDateTime.parse("2026-10-21T15:00:00+09:00");

    @Autowired private OrderUseCase useCase;
    @Autowired private EntityManager entityManager;
    @Autowired private PlatformTransactionManager transactionManager;
    @MockitoBean private PaymentGateway paymentGateway;

    @DisplayName("[INV-ORDER-46] PG 결제가 완료되면 주문 품목의 재고 예약을 확정하고 주문 상태를 CONFIRMED로 바꾼다.")
    @Nested
    class ConfirmOrderAfterProviderApproval {

        @DisplayName("[INV-STOCK-49] PG 성공으로 예약을 확정한 뒤 가용 재고는 예약 직후와 같다. 예약 확정에 따른 추가 차감은 0이다.")
        @Nested
        class ReservationConsumptionDoesNotDeductTwice {

            @Test
            void consumesReservationAfterProviderApproval() {
                Scenario scenario = confirmationScenario(5);
                String attemptKey = newAttemptKey();
                when(paymentGateway.approve(any())).thenAnswer(invocation -> approved(invocation.getArgument(0)));

                useCase.confirm(scenario.buyerId(), scenario.orderId(), attemptKey);
                entityManager.flush();
                entityManager.clear();
                Order confirmed = entityManager.find(Order.class, scenario.orderId());

                assertAll(
                    () -> assertThat(confirmed.getStatus()).isEqualTo(OrderStatus.CONFIRMED),
                    () -> assertThat(stockReservationStatus(scenario.orderId())).isEqualTo("CONSUMED"),
                    () -> assertThat(confirmed.getPayment().status()).isEqualTo(PaymentStatus.SUCCESS),
                    () -> assertThat(TestEntities.stockQuantity(entityManager, scenario.productId())).isEqualTo(3),
                    () -> verify(paymentGateway).approve(any())
                );
            }
        }
    }

    @DisplayName("[카드 승인 거절] 주문은 DRAFT로 남고 재고 예약을 반환한다.")
    @Test
    void releasesReservationAfterCardRejection() {
        Scenario scenario = confirmationScenario(5);
        String attemptKey = newAttemptKey();
        when(paymentGateway.approve(any())).thenReturn(new PaymentGateway.ProviderError(
            403, "REJECT_CARD_PAYMENT", "한도초과 혹은 잔액부족으로 결제에 실패했습니다."));

        CoreException failure = assertThrows(CoreException.class,
            () -> useCase.confirm(scenario.buyerId(), scenario.orderId(), attemptKey));
        entityManager.flush();
        entityManager.clear();
        Order order = entityManager.find(Order.class, scenario.orderId());

        assertAll(
            () -> assertThat(failure.getErrorCode().name()).isEqualTo("PAYMENT_FAILED"),
            () -> assertThat(order.getStatus()).isEqualTo(OrderStatus.DRAFT),
            () -> assertThat(order.getPayment().status()).isEqualTo(PaymentStatus.FAILED),
            () -> assertThat(stockReservationStatus(scenario.orderId())).isNull(),
            () -> assertThat(TestEntities.stockQuantity(entityManager, scenario.productId())).isEqualTo(5),
            () -> verify(paymentGateway).approve(any())
        );
    }

    @DisplayName("[상태 전이] PG 결과가 UNKNOWN이면 주문을 미확정으로 두고 재고 예약을 반환하지 않는다.")
    @Test
    void keepsReservationWhenPaymentOutcomeIsUnknown() {
        Scenario scenario = confirmationScenario(5);
        String attemptKey = newAttemptKey();
        when(paymentGateway.approve(any())).thenReturn(new PaymentGateway.Unknown());

        Order result = useCase.confirm(scenario.buyerId(), scenario.orderId(), attemptKey);
        entityManager.flush();
        entityManager.clear();
        Order pending = entityManager.find(Order.class, scenario.orderId());

        assertAll(
            () -> assertThat(result.getStatus()).isEqualTo(OrderStatus.DRAFT),
            () -> assertThat(pending.getStatus()).isEqualTo(OrderStatus.DRAFT),
            () -> assertThat(pending.getPayment().status()).isEqualTo(PaymentStatus.UNKNOWN),
            () -> assertThat(stockReservationStatus(scenario.orderId())).isEqualTo("RESERVED"),
            () -> assertThat(TestEntities.stockQuantity(entityManager, scenario.productId())).isEqualTo(3),
            () -> verify(paymentGateway).approve(any())
        );
    }

    @DisplayName("[INV-ORDER-45] PG 결제 요청 전에 주문 품목의 재고를 예약한다.")
    @Nested
    class ReserveStockBeforeProviderApproval {

        @DisplayName("[INV-STOCK-47] PG 요청 전에 주문 품목 수량만큼 재고가 예약되어 있고 그 수량은 가용 재고에서 제외된 상태다.")
        @Nested
        class ReservedQuantityIsExcludedFromAvailableStock {

            @Test
            @Transactional(propagation = Propagation.NOT_SUPPORTED)
            void persistsPendingPaymentBeforeSynchronousProviderCall() {
                Scenario scenario = createScenario();
                String attemptKey = newAttemptKey();
                AtomicBoolean transactionActiveAtApproval = new AtomicBoolean(true);
                AtomicReference<PaymentFlowState> stateAtApproval = new AtomicReference<>();
                when(paymentGateway.approve(any())).thenAnswer(invocation -> {
                    transactionActiveAtApproval.set(TransactionSynchronizationManager.isActualTransactionActive());
                    stateAtApproval.set(readPaymentFlowState(scenario.orderId(), scenario.productId()));
                    PaymentGateway.ApprovalRequest request = invocation.getArgument(0);
                    return new PaymentGateway.Approved(
                        request.paymentKey(), request.providerOrderId(), request.amount(), APPROVED_AT);
                });

                useCase.confirm(scenario.buyerId(), scenario.orderId(), attemptKey);

                assertAll(
                    () -> assertThat(transactionActiveAtApproval).isFalse(),
                    () -> assertThat(stateAtApproval).hasValue(
                        new PaymentFlowState(OrderStatus.DRAFT, PaymentStatus.PENDING, "RESERVED", 3)),
                    () -> verify(paymentGateway).approve(any())
                );
            }
        }
    }

    @DisplayName("[INV-PAYMENT-51] 이미 사용한 멱등키의 결제 요청을 PG로 다시 보내지 않는다.")
    @Nested
    class RejectProcessedPaymentAttempt {

        @DisplayName("[상태 전이] SUCCESS 결제에 같은 멱등키로 재요청하면 PAYMENT_ALREADY_PROCESSED이고 PG를 호출하지 않는다.")
        @Test
        void rejectsRetryForSuccessfulPayment() {
            Scenario scenario = confirmationScenario(5);
            Payment payment = new Payment(PaymentMethod.PG, 4_000L, PROCESSED_ATTEMPT_KEY,
                "LP-ORD-0000000042");
            payment.recordResult(new PaymentGateway.Approved(
                "test-payment-42", "LP-ORD-0000000042", 4_000L, APPROVED_AT));
            attachPayment(scenario.orderId(), payment);

            CoreException failure = assertThrows(CoreException.class,
                () -> useCase.confirm(scenario.buyerId(), scenario.orderId(), PROCESSED_ATTEMPT_KEY));
            entityManager.clear();
            Order order = entityManager.find(Order.class, scenario.orderId());

            assertAll(
                () -> assertThat(failure.getErrorCode()).isEqualTo(ErrorCode.PAYMENT_ALREADY_PROCESSED),
                () -> assertThat(order.getStatus()).isEqualTo(OrderStatus.DRAFT),
                () -> assertThat(order.getPayment().status()).isEqualTo(PaymentStatus.SUCCESS),
                () -> org.mockito.Mockito.verifyNoInteractions(paymentGateway)
            );
        }

        @DisplayName("[상태 전이] FAILED 결제에 같은 멱등키로 재요청하면 PAYMENT_ALREADY_PROCESSED이고 PG를 호출하지 않는다.")
        @Test
        void rejectsRetryForFailedPayment() {
            Scenario scenario = confirmationScenario(5);
            Payment payment = new Payment(PaymentMethod.PG, 4_000L, PROCESSED_ATTEMPT_KEY,
                "LP-ORD-0000000042");
            payment.recordResult(new PaymentGateway.ProviderError(403, "REJECT_CARD_PAYMENT", "거절"));
            attachPayment(scenario.orderId(), payment);

            CoreException failure = assertThrows(CoreException.class,
                () -> useCase.confirm(scenario.buyerId(), scenario.orderId(), PROCESSED_ATTEMPT_KEY));
            entityManager.clear();
            Order order = entityManager.find(Order.class, scenario.orderId());

            assertAll(
                () -> assertThat(failure.getErrorCode()).isEqualTo(ErrorCode.PAYMENT_ALREADY_PROCESSED),
                () -> assertThat(order.getStatus()).isEqualTo(OrderStatus.DRAFT),
                () -> assertThat(order.getPayment().status()).isEqualTo(PaymentStatus.FAILED),
                () -> org.mockito.Mockito.verifyNoInteractions(paymentGateway)
            );
        }

        @DisplayName("[상태 전이] UNKNOWN 결제는 결과 대기를 유지하고 PG를 다시 호출하지 않는다.")
        @Test
        void doesNotRetryUnknownPayment() {
            Scenario scenario = confirmationScenario(5);
            Payment payment = new Payment(PaymentMethod.PG, 4_000L, PROCESSED_ATTEMPT_KEY,
                "LP-ORD-0000000042");
            payment.recordResult(new PaymentGateway.Unknown());
            attachPayment(scenario.orderId(), payment);

            Order result = useCase.confirm(scenario.buyerId(), scenario.orderId(), PROCESSED_ATTEMPT_KEY);
            entityManager.flush();
            entityManager.clear();
            Order pending = entityManager.find(Order.class, scenario.orderId());

            assertAll(
                () -> assertThat(result.getStatus()).isEqualTo(OrderStatus.DRAFT),
                () -> assertThat(pending.getStatus()).isEqualTo(OrderStatus.DRAFT),
                () -> assertThat(pending.getPayment().status()).isEqualTo(PaymentStatus.UNKNOWN),
                () -> org.mockito.Mockito.verifyNoInteractions(paymentGateway)
            );
        }
    }

    private void attachPayment(Long orderId, Payment payment) {
        Order order = entityManager.find(Order.class, orderId);
        order.setPayment(payment);
        entityManager.flush();
        entityManager.clear();
    }

    private static String newAttemptKey() {
        return "test-attempt-flow-" + UUID.randomUUID();
    }

    private Scenario createScenario() {
        return inTransaction(() -> {
            Product product = new Product(1L, "PG 결제 상품 " + UUID.randomUUID(), 2_000L);
            entityManager.persist(product);
            entityManager.flush();
            entityManager.persist(new Stock(product.getId(), 5));
            User buyer = new User();
            entityManager.persist(buyer);
            Order order = new Order(buyer.getId(), List.of(OrderItem.of(product, 2)));
            entityManager.persist(order);
            entityManager.flush();
            return new Scenario(buyer.getId(), order.getId(), product.getId());
        });
    }

    private Scenario confirmationScenario(int initialStock) {
        Product product = new Product(1L, "PG 승인 상품 " + UUID.randomUUID(), 2_000L);
        entityManager.persist(product);
        entityManager.flush();
        entityManager.persist(new Stock(product.getId(), initialStock));
        User buyer = new User();
        entityManager.persist(buyer);
        entityManager.persist(new Point(buyer.getId(), 10_000L));
        Order order = new Order(buyer.getId(), List.of(OrderItem.of(product, 2)));
        entityManager.persist(order);
        entityManager.flush();
        return new Scenario(buyer.getId(), order.getId(), product.getId());
    }

    private PaymentGateway.Approved approved(PaymentGateway.ApprovalRequest request) {
        return new PaymentGateway.Approved(
            request.paymentKey(), request.providerOrderId(), request.amount(), APPROVED_AT);
    }

    private String stockReservationStatus(Long orderId) {
        Object status = entityManager.createQuery(
                "select o.stockReservationStatus from Order o where o.id = :orderId", Object.class)
            .setParameter("orderId", orderId)
            .getSingleResult();
        return status == null ? null : status.toString();
    }

    private PaymentFlowState readPaymentFlowState(Long orderId, Long productId) {
        return inTransaction(() -> {
            entityManager.clear();
            Order order = entityManager.find(Order.class, orderId);
            Payment payment = order.getPayment();
            return new PaymentFlowState(
                order.getStatus(),
                payment == null ? null : payment.status(),
                stockReservationStatus(orderId),
                TestEntities.stockQuantity(entityManager, productId));
        });
    }

    private <T> T inTransaction(java.util.function.Supplier<T> action) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return transaction.execute(status -> action.get());
    }

    private record Scenario(Long buyerId, Long orderId, Long productId) {
    }

    private record PaymentFlowState(
        OrderStatus orderStatus,
        PaymentStatus paymentStatus,
        String reservationStatus,
        int stockQuantity
    ) {
    }
}
