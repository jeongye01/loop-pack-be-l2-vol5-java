package com.loopers.support.transaction;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.function.Supplier;

@Component
public class TransactionRetryExecutor {

    private final TransactionTemplate newTransaction;
    private final int maxRetries;
    private final long backoffMillis;

    public TransactionRetryExecutor(
        PlatformTransactionManager transactionManager,
        @Value("${concurrency.transaction-retry.max-retries:1}") int maxRetries,
        @Value("${concurrency.transaction-retry.backoff-ms:0}") long backoffMillis
    ) {
        this.newTransaction = new TransactionTemplate(transactionManager);
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.maxRetries = maxRetries;
        this.backoffMillis = backoffMillis;
    }

    public boolean isEnabled() {
        return maxRetries > 0;
    }

    public <T> T execute(Supplier<T> operation) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            return operation.get();
        }
        RuntimeException failure = null;
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                return newTransaction.execute(status -> operation.get());
            } catch (OptimisticLockingFailureException exception) {
                failure = exception;
                if (attempt == maxRetries) {
                    throw exception;
                }
                waitBeforeRetry();
            }
        }
        throw failure;
    }

    private void waitBeforeRetry() {
        if (backoffMillis <= 0) {
            return;
        }
        try {
            Thread.sleep(backoffMillis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Transaction retry interrupted", exception);
        }
    }
}
