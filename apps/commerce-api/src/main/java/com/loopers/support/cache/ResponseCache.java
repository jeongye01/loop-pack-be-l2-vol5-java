package com.loopers.support.cache;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

@Component
public class ResponseCache {
    private final CachedJson cached;
    private final CacheVersions versions;
    private final ObjectMapper json;
    public ResponseCache(CachedJson cached, CacheVersions versions, ObjectMapper json) {
        this.cached = cached; this.versions = versions; this.json = json;
    }
    public <T> T read(String cacheName, String apiKey, TypeReference<T> type, Supplier<T> loader, String... namespaces) {
        return read(snapshot(namespaces), cacheName, apiKey, type, loader, namespaces);
    }
    public CacheVersions.Snapshot snapshot(String... namespaces) {
        return versions.snapshot(namespaces);
    }
    public <T> T read(CacheVersions.Snapshot snapshot, String cacheName, String apiKey, TypeReference<T> type,
                      Supplier<T> loader, String... namespaces) {
        if (!snapshot.enabled()) return loader.get();
        String key = snapshot.key(apiKey, namespaces);
        String value = cached.load(cacheName, key, () -> {
            try { return json.writeValueAsString(loader.get()); }
            catch (com.fasterxml.jackson.core.JsonProcessingException e) { throw new IllegalStateException(e); }
        });
        try { return json.readValue(value, type); }
        catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            cached.evict(cacheName, key);
            return loader.get();
        }
    }
}
