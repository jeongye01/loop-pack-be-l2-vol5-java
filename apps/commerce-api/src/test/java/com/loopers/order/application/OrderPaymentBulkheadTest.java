package com.loopers.order.application;

import com.loopers.order.domain.Order;
import com.loopers.order.domain.OrderRepository;
import com.loopers.order.domain.PaymentGateway;
import com.loopers.product.domain.ProductRepository;
import com.loopers.product.domain.StockRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorCode;
import com.loopers.support.transaction.TransactionRetryExecutor;
import com.loopers.user.domain.PointRepository;
import com.loopers.user.domain.UserRepository;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("주문 결제 Bulkhead")
class OrderPaymentBulkheadTest {

    @Test
    @DisplayName("동시 결제 한도가 차면 TX A 전에 빠르게 거절하고 결제·재고를 변경하지 않는다")
    void rejectsBeforePreparingPaymentWhenBulkheadIsFull() {
        Bulkhead bulkhead = Bulkhead.of("test-payment-flow", BulkheadConfig.custom()
            .maxConcurrentCalls(1)
            .maxWaitDuration(Duration.ZERO)
            .build());
        assertThat(bulkhead.tryAcquirePermission()).isTrue();
        OrderPaymentTransactionService transactionService = mock(OrderPaymentTransactionService.class);
        PaymentGateway paymentGateway = mock(PaymentGateway.class);
        OrderUseCase useCase = useCase(transactionService, paymentGateway, bulkhead);

        CoreException failure = assertThrows(CoreException.class,
            () -> useCase.confirm(1L, 10L, "payment-key", "attempt-key"));

        assertThat(failure.getErrorCode()).isEqualTo(ErrorCode.PAYMENT_FAILED);
        verifyNoInteractions(transactionService, paymentGateway);
    }

    @Test
    @DisplayName("결제 흐름이 끝나면 Bulkhead 허가를 반환한다")
    void releasesBulkheadPermissionAfterPaymentFlow() {
        Bulkhead bulkhead = Bulkhead.of("test-payment-flow", BulkheadConfig.custom()
            .maxConcurrentCalls(1)
            .maxWaitDuration(Duration.ZERO)
            .build());
        OrderPaymentTransactionService transactionService = mock(OrderPaymentTransactionService.class);
        Order order = mock(Order.class);
        when(transactionService.prepare(1L, 10L, "payment-key", "attempt-key"))
            .thenReturn(new OrderPaymentTransactionService.DispatchPlan(order, null));
        OrderUseCase useCase = useCase(transactionService, mock(PaymentGateway.class), bulkhead);

        assertThat(useCase.confirm(1L, 10L, "payment-key", "attempt-key")).isSameAs(order);

        assertThat(bulkhead.tryAcquirePermission()).isTrue();
        bulkhead.releasePermission();
    }

    private OrderUseCase useCase(
        OrderPaymentTransactionService transactionService,
        PaymentGateway paymentGateway,
        Bulkhead bulkhead
    ) {
        return new OrderUseCase(
            mock(OrderRepository.class), mock(ProductRepository.class), mock(UserRepository.class),
            mock(PointRepository.class), mock(StockRepository.class), mock(TransactionRetryExecutor.class),
            mock(EntityManager.class), transactionService, paymentGateway, bulkhead);
    }
}
