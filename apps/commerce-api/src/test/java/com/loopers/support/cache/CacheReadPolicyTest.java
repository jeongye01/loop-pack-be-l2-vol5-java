package com.loopers.support.cache;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CacheReadPolicyTest {
    @Test void defaultKeepsRevisionChecks() {
        assertThat(new CacheReadPolicy(false, new MockEnvironment()).bypassRevision()).isFalse();
    }
    @Test void bypassRequiresExplicitExperimentProfile() {
        assertThatThrownBy(() -> new CacheReadPolicy(true, new MockEnvironment())).isInstanceOf(IllegalStateException.class);
    }
    @Test void eachExperimentInstanceUsesIsolatedKeys() {
        var environment = new MockEnvironment();
        environment.setActiveProfiles("experiment");
        var first = new CacheReadPolicy(true, environment);
        assertThat(first.bypassRevision()).isTrue();
        assertThat(first.experimentRevision()).startsWith("experiment-")
            .isNotEqualTo(new CacheReadPolicy(true, environment).experimentRevision());
    }
}
