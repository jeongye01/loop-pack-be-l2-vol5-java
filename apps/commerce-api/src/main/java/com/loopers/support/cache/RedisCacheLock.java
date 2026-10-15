package com.loopers.support.cache;

import com.loopers.config.redis.RedisConfig;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/** A leased refill lock, not a replacement for revision-based consistency. */
@Component
public class RedisCacheLock {
    static final String PREFIX = "catalog:refill-lock:";
    static final DefaultRedisScript<Long> RELEASE = new DefaultRedisScript<>(
        "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end", Long.class);
    private final RedisTemplate<String, String> redis;
    private final MeterRegistry metrics;
    private final Duration lease;
    final long waitNanos;
    private final long pollMillis;

    public RedisCacheLock(@Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> redis,
                          MeterRegistry metrics,
                          @Value("${catalog.cache.lock.lease-ms:5000}") long leaseMillis,
                          @Value("${catalog.cache.lock.wait-ms:1000}") long waitMillis,
                          @Value("${catalog.cache.lock.poll-ms:20}") long pollMillis) {
        if (leaseMillis <= 0 || waitMillis <= 0 || pollMillis <= 0) throw new IllegalArgumentException("Positive lock durations required");
        this.redis = redis; this.metrics = metrics; this.lease = Duration.ofMillis(leaseMillis);
        this.waitNanos = Duration.ofMillis(waitMillis).toNanos(); this.pollMillis = pollMillis;
    }

    record Attempt(String key, String token, boolean acquired, boolean failed) { }

    Attempt acquire(ResponseCache.CacheKey key) {
        String physicalKey = PREFIX + key.cacheName() + ":" + key.revisionKey();
        String token = UUID.randomUUID().toString();
        try {
            boolean acquired = Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(physicalKey, token, lease));
            record(acquired ? "acquired" : "contended");
            return new Attempt(physicalKey, token, acquired, false);
        } catch (RuntimeException e) {
            record("acquire_error");
            return new Attempt(physicalKey, token, false, true);
        }
    }

    void release(Attempt attempt) {
        try { redis.execute(RELEASE, List.of(attempt.key()), attempt.token()); }
        catch (RuntimeException e) { record("release_error"); }
    }

    void pause(long remainingNanos) {
        try { java.util.concurrent.TimeUnit.NANOSECONDS.sleep(Math.min(remainingNanos, Duration.ofMillis(pollMillis).toNanos())); }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for cache refill", e);
        }
    }

    void record(String outcome) { metrics.counter("catalog.cache.lock", "outcome", outcome).increment(); }
}
