package com.loopers.support.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

class RedisCacheLockTest {
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.0")).withExposedPorts(6379);
    static LettuceConnectionFactory connections;
    static StringRedisTemplate redis;
    @BeforeAll static void start() {
        REDIS.start();
        connections = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getFirstMappedPort());
        connections.afterPropertiesSet();
        redis = new StringRedisTemplate(connections);
    }
    @AfterAll static void stop() { connections.destroy(); REDIS.stop(); }
    RedisCacheLock coordinator() { return new RedisCacheLock(redis, new SimpleMeterRegistry(), 5000, 1000, 20); }

    @Test @DisplayName("별도 조율자도 같은 키에는 한 소유자만 얻고 다른 키·리비전은 독립적이다")
    void sharedRedisCoordinatesOwners() {
        var a = coordinator(); var b = coordinator();
        var key = new ResponseCache.CacheKey("products", "shared:v1");
        var first = a.acquire(key);
        assertThat(first.acquired()).isTrue();
        assertThat(b.acquire(key).acquired()).isFalse();
        var other = b.acquire(new ResponseCache.CacheKey("products", "shared:v2"));
        assertThat(other.acquired()).isTrue();
        assertThat(redis.getExpire(first.key())).isBetween(1L, 5L);
        a.release(first); b.release(other);
        var next = b.acquire(key);
        assertThat(next.acquired()).isTrue(); b.release(next);
    }

    @Test @DisplayName("만료된 소유자의 해제는 새 소유자의 락을 삭제하지 않는다")
    void expiredOwnerCannotDeleteSuccessor() {
        var lock = coordinator(); var key = new ResponseCache.CacheKey("products", "expired");
        var old = lock.acquire(key);
        redis.expire(old.key(), Duration.ZERO);
        var current = lock.acquire(key);
        assertThat(current.acquired()).isTrue();
        lock.release(old);
        assertThat(redis.opsForValue().get(current.key())).isEqualTo(current.token());
        lock.release(current);
    }

    @Test @DisplayName("재채움 실패에도 소유한 락을 해제한다")
    void loaderFailureReleasesLock() {
        var lock = coordinator();
        var cache = mock(ResponseCache.class);
        var key = new ResponseCache.CacheKey("products", "failure");
        when(cache.hitOnly(any())).thenAnswer(inv -> { throw miss(key); });
        var tx = mock(PlatformTransactionManager.class);
        when(tx.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        var reader = new CatalogReadTransaction(tx, mock(CacheReadPolicy.class), cache, lock);
        assertThatThrownBy(() -> reader.read(() -> { throw new IllegalArgumentException("origin"); })).isInstanceOf(IllegalArgumentException.class);
        assertThat(redis.hasKey(RedisCacheLock.PREFIX + "products:failure")).isFalse();
        verify(tx).rollback(any());
    }

    @Test @DisplayName("대기자는 DB 트랜잭션 없이 재조회하고 자기 요청의 결과를 반환한다")
    void followerRechecksWithoutTransaction() {
        var owner = coordinator(); var key = new ResponseCache.CacheKey("products", "waiter");
        var held = owner.acquire(key);
        var cache = mock(ResponseCache.class); var count = new AtomicInteger();
        when(cache.hitOnly(any())).thenAnswer(inv -> {
            if (count.getAndIncrement() == 0) throw miss(key);
            return "own-response";
        });
        var tx = mock(PlatformTransactionManager.class);
        var reader = new CatalogReadTransaction(tx, mock(CacheReadPolicy.class), cache, coordinator());
        try { assertThat(reader.read(() -> "unused")).isEqualTo("own-response"); }
        finally { owner.release(held); }
        verifyNoInteractions(tx);
    }

    @Test @DisplayName("최대 대기 초과 시 DB fallback하고 다른 요청의 락은 해제하지 않는다")
    void timeoutFallback() {
        var key = new ResponseCache.CacheKey("products", "timeout"); var owner = coordinator(); var held = owner.acquire(key);
        var metrics = new SimpleMeterRegistry();
        var lock = new RedisCacheLock(redis, metrics, 5000, 30, 10);
        var cache = mock(ResponseCache.class);
        when(cache.hitOnly(any())).thenAnswer(inv -> { throw miss(key); });
        var tx = mock(PlatformTransactionManager.class);
        when(tx.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        try {
            assertThat(new CatalogReadTransaction(tx, mock(CacheReadPolicy.class), cache, lock).read(() -> "db")).isEqualTo("db");
            assertThat(redis.opsForValue().get(held.key())).isEqualTo(held.token());
            assertThat(metrics.get("catalog.cache.lock").tag("outcome", "timeout_fallback").counter().count()).isEqualTo(1);
            verify(tx).commit(any());
        } finally { owner.release(held); }
    }

    @Test @DisplayName("Redis 락 오류는 DB fallback하며 해제를 시도하지 않는다")
    void redisErrorFallback() {
        var broken = mock(StringRedisTemplate.class);
        when(broken.opsForValue()).thenThrow(new IllegalStateException("Redis unavailable"));
        var metrics = new SimpleMeterRegistry(); var lock = new RedisCacheLock(broken, metrics, 5000, 1000, 20);
        var cache = mock(ResponseCache.class);
        when(cache.hitOnly(any())).thenAnswer(inv -> { throw miss(new ResponseCache.CacheKey("products", "error")); });
        var tx = mock(PlatformTransactionManager.class);
        when(tx.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        assertThat(new CatalogReadTransaction(tx, mock(CacheReadPolicy.class), cache, lock).read(() -> "db")).isEqualTo("db");
        assertThat(metrics.get("catalog.cache.lock").tag("outcome", "acquire_error").counter().count()).isEqualTo(1);
        verify(tx).commit(any());
    }

    private ResponseCache.CacheMiss miss(ResponseCache.CacheKey key) {
        return new ResponseCache.CacheMiss(key);
    }
}
