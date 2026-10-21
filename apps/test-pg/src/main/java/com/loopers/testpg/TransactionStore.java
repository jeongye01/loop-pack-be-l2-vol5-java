package com.loopers.testpg;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class TransactionStore {

    private final ConcurrentMap<String, Transaction> transactions = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Submission> submissionsByIdempotencyKey = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, String> transactionsByPaymentKey = new ConcurrentHashMap<>();

    public synchronized Submission confirm(String idempotencyKey, String paymentKey, String orderId, long amount) {
        Submission previousSubmission = previousSubmission(idempotencyKey);
        if (previousSubmission != null) {
            return previousSubmission;
        }

        if (transactionsByPaymentKey.containsKey(paymentKey)) {
            return cache(idempotencyKey, new Submission(null, true, null, null, 400,
                "ALREADY_PROCESSED_PAYMENT", "이미 처리된 결제 입니다."));
        }

        String transactionKey = "test-txn-" + UUID.randomUUID();
        OffsetDateTime approvedAt = OffsetDateTime.now(ZoneOffset.UTC);
        Transaction transaction = new Transaction(transactionKey, paymentKey, orderId, amount,
            TransactionStatus.SUCCESS);
        transactions.put(transactionKey, transaction);
        transactionsByPaymentKey.put(paymentKey, transactionKey);
        Submission approved = new Submission(transaction, false, paymentKey, approvedAt, 200, null, null);
        return cache(idempotencyKey, approved);
    }

    public synchronized Submission error(String idempotencyKey, int status, String code, String message) {
        Submission previousSubmission = previousSubmission(idempotencyKey);
        if (previousSubmission != null) {
            return previousSubmission;
        }
        return cache(idempotencyKey, new Submission(null, false, null, null, status, code, message));
    }

    private Submission previousSubmission(String idempotencyKey) {
        return idempotencyKey == null ? null : submissionsByIdempotencyKey.get(idempotencyKey);
    }

    private Submission cache(String idempotencyKey, Submission submission) {
        if (idempotencyKey != null) {
            submissionsByIdempotencyKey.put(idempotencyKey, submission);
        }
        return submission;
    }

    public Optional<Transaction> findByTransactionKey(String transactionKey) {
        return Optional.ofNullable(transactions.get(transactionKey));
    }

    public List<Transaction> findByOrderId(String orderId) {
        return transactions.values().stream()
            .filter(transaction -> transaction.orderId().equals(orderId))
            .sorted(Comparator.comparing(Transaction::transactionKey))
            .toList();
    }

    public Optional<Transaction> updateStatus(String transactionKey, TransactionStatus status) {
        return Optional.ofNullable(transactions.computeIfPresent(transactionKey,
            (key, transaction) -> transaction.status() == TransactionStatus.PENDING
                ? transaction.withStatus(status)
                : transaction));
    }

    public record Transaction(String transactionKey, String paymentKey, String orderId, long amount,
                              TransactionStatus status) {
        public Transaction withStatus(TransactionStatus nextStatus) {
            return new Transaction(transactionKey, paymentKey, orderId, amount, nextStatus);
        }
    }

    public record Submission(Transaction transaction, boolean alreadyProcessed, String paymentKey,
                             OffsetDateTime approvedAt, int httpStatus, String errorCode, String errorMessage) {
    }

    public enum TransactionStatus {
        PENDING,
        SUCCESS,
        FAILED
    }
}
