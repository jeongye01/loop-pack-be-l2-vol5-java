package com.loopers.support.cache;

import java.util.function.Supplier;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

@Component
public class CachedJson {
    @Cacheable(cacheResolver = "readCacheResolver", key = "#key")
    public String load(String cacheName, String key, Supplier<String> loader) { return loader.get(); }
    @org.springframework.cache.annotation.CacheEvict(cacheResolver = "readCacheResolver", key = "#key")
    public void evict(String cacheName, String key) { }
}
