package com.tanmay.ratelimiter.web;

import com.tanmay.ratelimiter.core.RateLimiterService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * Demo endpoints sitting behind the rate limiter. Hit {@code /api/ping}
 * repeatedly to watch the bucket drain and refill.
 */
@RestController
@RequestMapping("/api")
public class DemoController {

    private final RateLimiterService rateLimiterService;

    public DemoController(RateLimiterService rateLimiterService) {
        this.rateLimiterService = rateLimiterService;
    }

    @GetMapping("/ping")
    public Map<String, Object> ping() {
        return Map.of(
                "message", "pong",
                "timestamp", Instant.now().toString());
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        return Map.of(
                "trackedClients", rateLimiterService.trackedClients(),
                "timestamp", Instant.now().toString());
    }
}
