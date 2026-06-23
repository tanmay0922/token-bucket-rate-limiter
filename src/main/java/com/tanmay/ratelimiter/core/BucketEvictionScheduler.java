package com.tanmay.ratelimiter.core;

import com.tanmay.ratelimiter.config.RateLimitProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.stereotype.Component;

/**
 * Periodically evicts idle buckets so memory stays bounded no matter how many
 * distinct clients have been seen. Disabled by setting
 * {@code ratelimit.eviction.enabled=false}.
 *
 * <p>The sweep is registered through {@link SchedulingConfigurer} rather than a
 * {@code @Scheduled(fixedDelayString = ...)} annotation: that lets us hand the
 * already-parsed {@link java.time.Duration} straight to the registrar, so the
 * friendly {@code 1m}/{@code 10m} config format works (annotation string parsing
 * only accepts plain millis or ISO-8601 like {@code PT1M}).
 */
@Component
@ConditionalOnProperty(prefix = "ratelimit.eviction", name = "enabled", havingValue = "true", matchIfMissing = true)
public class BucketEvictionScheduler implements SchedulingConfigurer {

    private static final Logger log = LoggerFactory.getLogger(BucketEvictionScheduler.class);

    private final RateLimiterService rateLimiterService;
    private final RateLimitProperties properties;

    public BucketEvictionScheduler(RateLimiterService rateLimiterService, RateLimitProperties properties) {
        this.rateLimiterService = rateLimiterService;
        this.properties = properties;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.addFixedDelayTask(this::sweep, properties.getEviction().getSweepInterval());
    }

    /** One cleanup pass; package-private so it can be invoked directly in tests. */
    void sweep() {
        int evicted = rateLimiterService.evictIdleBuckets(properties.getEviction().getIdleTtl());
        if (evicted > 0) {
            log.debug("Evicted {} idle bucket(s); {} client(s) still tracked",
                    evicted, rateLimiterService.trackedClients());
        }
    }
}
