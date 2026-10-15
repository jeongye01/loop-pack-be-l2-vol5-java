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
    private final CacheSingleFlight flights;
    public CatalogReadTransaction(PlatformTransactionManager manager, CacheReadPolicy policy, ResponseCache cache, CacheSingleFlight flights) {
        this.transaction = new TransactionTemplate(manager);
        this.transaction.setReadOnly(true);
        this.policy = policy;
        this.cache = cache;
        this.flights = flights;
    }
    public <T> T read(Supplier<T> operation) {
        while (true) {
            try { return cache.hitOnly(operation); }
            catch (ResponseCache.CacheMiss miss) {
                if (miss.key == null) return transaction.execute(status -> operation.get());
                // Acquire before opening a DB transaction. Only the owner starts a snapshot.
                var result = flights.execute(miss.key, () -> transaction.execute(status -> operation.get()));
                if (result.owner()) return result.value();
                // Never share the owner's composed response: refresh revisions and all cache reads.
            }
        }
    }
}
