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
    public record Entry<T>(String cacheName, String apiKey, TypeReference<T> type, Supplier<T> loader, String... namespaces) { }
    public record Pair<A, B>(A first, B second) { }

    public <A, B> Pair<A, B> readPair(CacheVersions.Snapshot snapshot, Entry<A> first, Entry<B> second) {
        if (!snapshot.enabled()) return new Pair<>(first.loader().get(), second.loader().get());
        String firstKey = snapshot.key(first.apiKey(), first.namespaces());
        String secondKey = snapshot.key(second.apiKey(), second.namespaces());
        var values = cached.loadMany(java.util.List.of(first.cacheName(), second.cacheName()), java.util.List.of(firstKey, secondKey));
        return new Pair<>(resolve(first, firstKey, values.get(0)), resolve(second, secondKey, values.get(1)));
    }

    private <T> T resolve(Entry<T> entry, String key, String value) {
        if (value == null) {
            T loaded = entry.loader().get();
            try { cached.store(entry.cacheName(), key, json.writeValueAsString(loaded)); }
            catch (com.fasterxml.jackson.core.JsonProcessingException e) { throw new IllegalStateException(e); }
            return loaded;
        }
        try { return json.readValue(value, entry.type()); }
        catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            cached.evict(entry.cacheName(), key);
            return entry.loader().get();
        }
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
