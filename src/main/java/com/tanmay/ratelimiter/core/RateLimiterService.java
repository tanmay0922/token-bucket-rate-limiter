package com.tanmay.ratelimiter.core;

import com.tanmay.ratelimiter.config.RateLimitProperties;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds one {@link TokenBucket} per client key and routes consume requests to it.
 *
 * <p>Buckets are created on first use via {@code computeIfAbsent}, which is
 * atomic on {@link ConcurrentHashMap} — two concurrent requests for a new key
 * will share a single bucket rather than racing to create two.
 */
@Service
public class RateLimiterService {

    private final ConcurrentHashMap<String, TokenBucket> buckets = new ConcurrentHashMap<>();
    private final RateLimitProperties properties;

    public RateLimiterService(RateLimitProperties properties) {
        this.properties = properties;
    }

    /** Try to spend one token for {@code clientKey}. */
    public ConsumptionProbe tryAcquire(String clientKey) {
        return tryAcquire(clientKey, 1);
    }

    public ConsumptionProbe tryAcquire(String clientKey, long tokens) {
        TokenBucket bucket = buckets.computeIfAbsent(clientKey, k ->
                new TokenBucket(properties.getCapacity(), properties.getRefillTokensPerSecond()));
        return bucket.tryConsume(tokens);
    }

    /** Number of distinct client keys currently tracked (useful for /metrics). */
    public int trackedClients() {
        return buckets.size();
    }

    /** Drop a client's bucket — e.g. on logout or for tests. */
    public void reset(String clientKey) {
        buckets.remove(clientKey);
    }

    /**
     * Remove every bucket idle for longer than {@code idleTtl}.
     *
     * <p>{@code removeIf} on a {@link ConcurrentHashMap} evaluates each entry
     * independently, so this is safe to run while requests are in flight. There
     * is a benign race: a bucket accessed between the idle check and removal may
     * be dropped and recreated full — that only ever *relaxes* the limit for one
     * already-idle client, never tightens it, so it is acceptable.
     *
     * @return the number of buckets evicted
     */
    public int evictIdleBuckets(Duration idleTtl) {
        long ttlMillis = idleTtl.toMillis();
        int before = buckets.size();
        buckets.values().removeIf(bucket -> bucket.idleMillis() > ttlMillis);
        return before - buckets.size();
    }
}
