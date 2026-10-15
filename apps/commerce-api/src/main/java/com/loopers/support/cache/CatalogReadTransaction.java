package com.loopers.support.cache;

import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Normal reads keep a DB snapshot; the fixed-data experiment skips the outer transaction. */
@Component
public class CatalogReadTransaction {
    private final TransactionTemplate transaction;
    private final CacheReadPolicy policy;
    public CatalogReadTransaction(PlatformTransactionManager manager, CacheReadPolicy policy) {
        this.transaction = new TransactionTemplate(manager);
        this.transaction.setReadOnly(true);
        this.policy = policy;
    }
    public <T> T read(Supplier<T> operation) {
        return policy.bypassRevision() ? operation.get() : transaction.execute(status -> operation.get());
    }
}
