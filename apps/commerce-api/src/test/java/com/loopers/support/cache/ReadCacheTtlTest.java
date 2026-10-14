package com.loopers.support.cache;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ReadCacheTtlTest {
    @Test
    void samplesAgainForEveryWriteIncludingTheSameKey() {
        long[] offsets = {-30, 0, 30};
        var calls = new AtomicInteger();
        var ttl = new ReadCacheTtl(() -> offsets[calls.getAndIncrement()]);
        assertThat(ttl.getTimeToLive("same-key", "value")).isEqualTo(Duration.ofSeconds(270));
        assertThat(ttl.getTimeToLive("same-key", "value")).isEqualTo(Duration.ofSeconds(300));
        assertThat(ttl.getTimeToLive("same-key", "value")).isEqualTo(Duration.ofSeconds(330));
        assertThat(calls.get()).isEqualTo(3);
    }

    @Test
    void defaultSamplerKeepsEveryTtlWithinTheAgreedRange() {
        var ttl = new ReadCacheTtl();
        for (int i = 0; i < 1000; i++) {
            assertThat(ttl.getTimeToLive("key", "value").toSeconds()).isBetween(270L, 330L);
        }
    }
}
