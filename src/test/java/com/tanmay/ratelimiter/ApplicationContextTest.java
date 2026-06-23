package com.tanmay.ratelimiter;

import com.tanmay.ratelimiter.core.BucketEvictionScheduler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Boots the full application context. This catches wiring/config errors that
 * pure unit tests miss — e.g. an invalid {@code @Scheduled} expression, which
 * only blows up when the context starts, not during plain class testing.
 */
@SpringBootTest
class ApplicationContextTest {

    @Autowired
    private BucketEvictionScheduler evictionScheduler;

    @Test
    void contextLoadsAndSchedulerIsWired() {
        assertNotNull(evictionScheduler, "eviction scheduler should be registered");
    }
}
