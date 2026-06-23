package com.tanmay.ratelimiter;

import com.tanmay.ratelimiter.config.RateLimitProperties;
import com.tanmay.ratelimiter.core.RateLimiterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BucketEvictionTest {

    private RateLimiterService service;

    @BeforeEach
    void setUp() {
        RateLimitProperties props = new RateLimitProperties();
        props.setCapacity(3);
        props.setRefillTokensPerSecond(1);
        service = new RateLimiterService(props);
    }

    @Test
    void evictsBucketsIdleBeyondTtl() throws InterruptedException {
        service.tryAcquire("idleClient");
        assertEquals(1, service.trackedClients());

        Thread.sleep(60); // let it sit idle

        int evicted = service.evictIdleBuckets(Duration.ofMillis(50));
        assertEquals(1, evicted);
        assertEquals(0, service.trackedClients());
    }

    @Test
    void keepsRecentlyUsedBuckets() throws InterruptedException {
        service.tryAcquire("oldClient");
        Thread.sleep(60);
        service.tryAcquire("freshClient"); // touched just now

        int evicted = service.evictIdleBuckets(Duration.ofMillis(50));
        assertEquals(1, evicted, "only the old client should be evicted");
        assertEquals(1, service.trackedClients());
    }

    @Test
    void evictedClientReturnsWithFullBucket() throws InterruptedException {
        // Drain the client.
        for (int i = 0; i < 3; i++) {
            service.tryAcquire("client");
        }
        assertFalse(service.tryAcquire("client").allowed());

        Thread.sleep(60);
        service.evictIdleBuckets(Duration.ofMillis(50));

        // Next access rebuilds a fresh, full bucket.
        assertTrue(service.tryAcquire("client").allowed());
        assertEquals(1, service.trackedClients());
    }
}
