package com.loopers.support.cache;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

@Component
public class ResponseCache {
    private final ThreadLocal<Boolean> hitOnly = new ThreadLocal<>();

    /** Probe the entire composed response without loading any original data. */
    <T> T hitOnly(Supplier<T> operation) {
        Boolean previous = hitOnly.get();
        hitOnly.set(true);
        try { return operation.get(); }
        finally {
            if (previous == null) hitOnly.remove();
            else hitOnly.set(previous);
        }
    }

    private <T> T loadOriginal(Supplier<T> loader, CacheKey key) {
        requireOriginalRead(key);
        return loader.get();
    }

    private void requireOriginalRead(CacheKey key) {
        if (Boolean.TRUE.equals(hitOnly.get())) throw new CacheMiss(key);
    }

    record CacheKey(String cacheName, String revisionKey) { }

    /** Internal control flow: restart with a fresh revision and DB snapshot. */
    static final class CacheMiss extends RuntimeException {
        final CacheKey key;
        CacheMiss(CacheKey key) { super(null, null, false, false); this.key = key; }
    }

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
    public record Entry<T>(String cacheName, String apiKey, TypeReference<T> type, Supplier<T> loader, String... namespaces) { }
    public record Pair<A, B>(A first, B second) { }

    public <A, B> Pair<A, B> readPair(CacheVersions.Snapshot snapshot, Entry<A> first, Entry<B> second) {
        if (!snapshot.enabled()) return new Pair<>(loadOriginal(first.loader(), null), loadOriginal(second.loader(), null));
        String firstKey = snapshot.key(first.apiKey(), first.namespaces());
        String secondKey = snapshot.key(second.apiKey(), second.namespaces());
        var values = cached.loadMany(java.util.List.of(first.cacheName(), second.cacheName()), java.util.List.of(firstKey, secondKey));
        return new Pair<>(resolve(first, firstKey, values.get(0)), resolve(second, secondKey, values.get(1)));
    }

    private <T> T resolve(Entry<T> entry, String key, String value) {
        if (value == null) {
            T loaded = loadOriginal(entry.loader(), new CacheKey(entry.cacheName(), key));
            try { cached.store(entry.cacheName(), key, json.writeValueAsString(loaded)); }
            catch (com.fasterxml.jackson.core.JsonProcessingException e) { throw new IllegalStateException(e); }
            return loaded;
        }
        try { return json.readValue(value, entry.type()); }
        catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            requireOriginalRead(new CacheKey(entry.cacheName(), key));
            cached.evict(entry.cacheName(), key);
            return loadOriginal(entry.loader(), new CacheKey(entry.cacheName(), key));
        }
    }
    public <T> T read(CacheVersions.Snapshot snapshot, String cacheName, String apiKey, TypeReference<T> type,
                      Supplier<T> loader, String... namespaces) {
        if (!snapshot.enabled()) return loadOriginal(loader, null);
        String key = snapshot.key(apiKey, namespaces);
        String value = cached.load(cacheName, key, () -> {
            try { return json.writeValueAsString(loadOriginal(loader, new CacheKey(cacheName, key))); }
            catch (com.fasterxml.jackson.core.JsonProcessingException e) { throw new IllegalStateException(e); }
        });
        try { return json.readValue(value, type); }
        catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            requireOriginalRead(new CacheKey(cacheName, key));
            cached.evict(cacheName, key);
            return loadOriginal(loader, new CacheKey(cacheName, key));
        }
    }
}
