package com.loopers.support.cache;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.assertj.core.api.Assertions.assertThat;

class ReadCacheTtlTest {
    @Test
    @DisplayName("같은 키도 저장마다 jitter를 다시 뽑아 TTL 270·300·330초를 생성한다")
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
    @DisplayName("기본 난수 생성기로 1,000번 생성한 TTL이 모두 270~330초 범위에 든다")
    void defaultSamplerKeepsEveryTtlWithinTheAgreedRange() {
        var ttl = new ReadCacheTtl();
        for (int i = 0; i < 1000; i++) {
            assertThat(ttl.getTimeToLive("key", "value").toSeconds()).isBetween(270L, 330L);
        }
    }
}
