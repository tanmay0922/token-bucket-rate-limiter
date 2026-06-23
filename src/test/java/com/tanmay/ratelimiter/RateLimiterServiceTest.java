package com.tanmay.ratelimiter;

import com.tanmay.ratelimiter.config.RateLimitProperties;
import com.tanmay.ratelimiter.core.RateLimiterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RateLimiterServiceTest {

    private RateLimiterService service;

    @BeforeEach
    void setUp() {
        RateLimitProperties props = new RateLimitProperties();
        props.setCapacity(3);
        props.setRefillTokensPerSecond(1);
        service = new RateLimiterService(props);
    }

    @Test
    void keepsSeparateBucketsPerClient() {
        // Drain client A completely.
        for (int i = 0; i < 3; i++) {
            assertTrue(service.tryAcquire("clientA").allowed());
        }
        assertFalse(service.tryAcquire("clientA").allowed());

        // Client B is unaffected.
        assertTrue(service.tryAcquire("clientB").allowed());
    }

    @Test
    void tracksClientCount() {
        service.tryAcquire("a");
        service.tryAcquire("b");
        assertEquals(2, service.trackedClients());
    }

    @Test
    void resetDropsBucket() {
        for (int i = 0; i < 3; i++) {
            service.tryAcquire("a");
        }
        assertFalse(service.tryAcquire("a").allowed());

        service.reset("a"); // fresh full bucket on next access
        assertTrue(service.tryAcquire("a").allowed());
    }
}
