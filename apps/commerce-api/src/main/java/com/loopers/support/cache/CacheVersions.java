package com.loopers.support.cache;

import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Authoritative revisions live in the same DB transaction as the write, not in Redis. */
@Component
public class CacheVersions {
    private final JdbcTemplate jdbc;
    private final boolean enabled;
    public CacheVersions(JdbcTemplate jdbc, @Value("${catalog.cache.enabled:false}") boolean enabled) {
        this.jdbc = jdbc;
        this.enabled = enabled;
    }
    public boolean isEnabled() { return enabled; }
    public String key(String apiKey, String... namespaces) {
        if (!enabled) return apiKey;
        StringBuilder key = new StringBuilder(apiKey);
        for (String namespace : namespaces) {
            var values = jdbc.queryForList("select revision from read_cache_revision where namespace = ?", String.class, namespace);
            key.append(":rev=").append(values.isEmpty() ? "0" : values.getFirst());
        }
        return key.toString();
    }
    public void changed(String namespace) {
        if (!enabled) return;
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Cache revision must change inside the database write transaction");
        }
        jdbc.update("insert into read_cache_revision(namespace, revision) values (?, ?) "
            + "on duplicate key update revision = values(revision)", namespace, UUID.randomUUID().toString());
    }
}
