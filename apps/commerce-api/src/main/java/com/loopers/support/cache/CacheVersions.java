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
    private final CacheReadPolicy policy;
    public CacheVersions(JdbcTemplate jdbc, @Value("${catalog.cache.enabled:false}") boolean enabled, CacheReadPolicy policy) {
        this.jdbc = jdbc;
        this.enabled = enabled;
        this.policy = policy;
    }
    public boolean isEnabled() { return enabled; }
    public Snapshot snapshot(String... namespaces) {
        if (!isEnabled()) return new Snapshot(false, java.util.Map.of());
        var revisions = new java.util.LinkedHashMap<String, String>();
        for (String namespace : namespaces) revisions.put(namespace, policy.bypassRevision() ? policy.experimentRevision() : "0");
        if (!policy.bypassRevision() && !revisions.isEmpty()) {
            String placeholders = String.join(",", java.util.Collections.nCopies(revisions.size(), "?"));
            jdbc.query("select namespace, revision from read_cache_revision where namespace in (" + placeholders + ")",
                (org.springframework.jdbc.core.RowCallbackHandler) row -> revisions.put(row.getString("namespace"), row.getString("revision")),
                revisions.keySet().toArray());
        }
        return new Snapshot(true, java.util.Map.copyOf(revisions));
    }
    public String key(String apiKey, String... namespaces) {
        return snapshot(namespaces).key(apiKey, namespaces);
    }
    public record Snapshot(boolean enabled, java.util.Map<String, String> revisions) {
        public Snapshot { revisions = java.util.Map.copyOf(revisions); }
        public String key(String apiKey, String... namespaces) {
            if (!enabled) return apiKey;
            StringBuilder key = new StringBuilder(apiKey);
            for (String namespace : namespaces) {
                if (!revisions.containsKey(namespace)) throw new IllegalArgumentException("Revision not loaded: " + namespace);
                key.append(":rev=").append(revisions.get(namespace));
            }
            return key.toString();
        }
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
