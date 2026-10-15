package com.loopers.support.cache;

import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Hits release the revision connection before Redis; misses restart in one DB snapshot. */
@Component
public class CatalogReadTransaction {
    private final TransactionTemplate transaction;
    private final CacheReadPolicy policy;
    private final ResponseCache cache;
    private final RedisCacheLock lock;
    public CatalogReadTransaction(PlatformTransactionManager manager, CacheReadPolicy policy, ResponseCache cache, RedisCacheLock lock) {
        this.transaction = new TransactionTemplate(manager);
        this.transaction.setReadOnly(true);
        this.policy = policy;
        this.cache = cache;
        this.lock = lock;
    }
    public <T> T read(Supplier<T> operation) {
        if (policy.bypassRevision()) return operation.get();
        long started = System.nanoTime();
        while (true) {
            try { return cache.hitOnly(operation); }
            catch (ResponseCache.CacheMiss miss) {
                if (miss.key == null) return original(operation);
                if (System.nanoTime() - started >= lock.waitNanos) {
                    lock.record("timeout_fallback");
                    return original(operation);
                }
                var attempt = lock.acquire(miss.key);
                if (attempt.failed()) return original(operation);
                if (attempt.acquired()) {
                    // The transaction rereads revision and cache before loading original data.
                    try { return original(operation); }
                    finally { lock.release(attempt); }
                }
                long remaining = lock.waitNanos - (System.nanoTime() - started);
                if (remaining > 0) lock.pause(remaining);
            }
        }
    }
    private <T> T original(Supplier<T> operation) {
        return transaction.execute(status -> operation.get());
    }
}
