package com.loopers.support.cache;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.LongSupplier;
import org.springframework.data.redis.cache.RedisCacheWriter;

/** A fresh TTL for each cache write; cache hits do not renew it. */
public final class ReadCacheTtl implements RedisCacheWriter.TtlFunction {
    public static final long BASE_SECONDS = 300;
    public static final long JITTER_SECONDS = 30;
    private final LongSupplier jitter;

    public ReadCacheTtl() {
        this(() -> ThreadLocalRandom.current().nextLong(-JITTER_SECONDS, JITTER_SECONDS + 1));
    }

    ReadCacheTtl(LongSupplier jitter) {
        this.jitter = jitter;
    }

    @Override
    public Duration getTimeToLive(Object key, Object value) {
        return Duration.ofSeconds(BASE_SECONDS + jitter.getAsLong());
    }
}
