package com.tanmay.ratelimiter;

import com.tanmay.ratelimiter.core.ConsumptionProbe;
import com.tanmay.ratelimiter.core.TokenBucket;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenBucketTest {

    @Test
    void startsFullAndAllowsBurstUpToCapacity() {
        TokenBucket bucket = new TokenBucket(5, 1);
        for (int i = 0; i < 5; i++) {
            assertTrue(bucket.tryConsume(1).allowed(), "token " + i + " should be allowed");
        }
        assertFalse(bucket.tryConsume(1).allowed(), "6th token exceeds capacity");
    }

    @Test
    void rejectsWhenEmptyAndReportsRetryAfter() {
        TokenBucket bucket = new TokenBucket(2, 2); // refills 2 tokens/sec
        bucket.tryConsume(2); // drain

        ConsumptionProbe probe = bucket.tryConsume(1);
        assertFalse(probe.allowed());
        assertEquals(0, probe.remainingTokens());
        // need 1 token at 2/sec -> ~500ms
        assertTrue(probe.retryAfterMillis() > 0 && probe.retryAfterMillis() <= 500,
                "retryAfter should be about 500ms but was " + probe.retryAfterMillis());
    }

    @Test
    void refillsOverTime() throws InterruptedException {
        TokenBucket bucket = new TokenBucket(10, 100); // 100 tokens/sec
        for (int i = 0; i < 10; i++) {
            bucket.tryConsume(1);
        }
        assertFalse(bucket.tryConsume(1).allowed());

        Thread.sleep(150); // ~15 tokens accrue, capped at capacity
        assertTrue(bucket.tryConsume(1).allowed(), "should have refilled after waiting");
    }

    @Test
    void neverRefillsAboveCapacity() throws InterruptedException {
        TokenBucket bucket = new TokenBucket(3, 1000);
        Thread.sleep(50); // would accrue 50 tokens if uncapped
        assertEquals(3, bucket.availableTokens());
    }

    @Test
    void rejectsInvalidConstructorArgs() {
        assertThrows(IllegalArgumentException.class, () -> new TokenBucket(0, 1));
        assertThrows(IllegalArgumentException.class, () -> new TokenBucket(1, 0));
    }

    @Test
    void isThreadSafeUnderConcurrentLoad() throws Exception {
        int capacity = 1000;
        TokenBucket bucket = new TokenBucket(capacity, 0.0001); // negligible refill during test
        ExecutorService pool = Executors.newFixedThreadPool(16);
        AtomicInteger granted = new AtomicInteger();

        Future<?>[] futures = new Future<?>[2000];
        for (int i = 0; i < futures.length; i++) {
            futures[i] = pool.submit(() -> {
                if (bucket.tryConsume(1).allowed()) {
                    granted.incrementAndGet();
                }
            });
        }
        for (Future<?> f : futures) {
            f.get();
        }
        pool.shutdown();

        // Exactly capacity tokens may be granted — no more, despite the race.
        assertEquals(capacity, granted.get());
    }
}
