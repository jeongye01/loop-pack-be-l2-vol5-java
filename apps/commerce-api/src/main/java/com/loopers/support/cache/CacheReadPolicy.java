package com.loopers.support.cache;

import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/** Diagnostic experiment only: disables revision reads/writes and immediate cache invalidation. */
@Component
public class CacheReadPolicy {
    private final boolean bypassRevision;
    private final String experimentRevision = "experiment-" + UUID.randomUUID();

    public CacheReadPolicy(@Value("${experiment.catalog-revision-bypass.enabled:false}") boolean bypassRevision, Environment environment) {
        if (bypassRevision && !environment.acceptsProfiles(Profiles.of("experiment"))) {
            throw new IllegalStateException("Revision bypass requires experiment profile");
        }
        this.bypassRevision = bypassRevision;
    }
    public boolean bypassRevision() { return bypassRevision; }
    public String experimentRevision() { return experimentRevision; }
}
