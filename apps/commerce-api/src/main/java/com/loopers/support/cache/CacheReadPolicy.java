package com.loopers.support.cache;

import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/** Fixed-data experiment only: never enable while exercising mutation/invalidation contracts. */
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
