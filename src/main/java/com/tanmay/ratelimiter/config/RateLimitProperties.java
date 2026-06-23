package com.tanmay.ratelimiter.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Rate limit settings, bound from the {@code ratelimit.*} keys in
 * application.yml. Every client key gets its own bucket with these parameters.
 */
@ConfigurationProperties(prefix = "ratelimit")
public class RateLimitProperties {

    /** Maximum tokens a bucket can hold — i.e. the largest allowed burst. */
    private long capacity = 10;

    /** Steady-state refill rate in tokens per second. */
    private double refillTokensPerSecond = 5.0;

    /** Header used to identify a client; falls back to remote IP when absent. */
    private String clientHeader = "X-API-Key";

    /** Idle-bucket eviction settings, to bound memory under many clients. */
    private final Eviction eviction = new Eviction();

    public long getCapacity() {
        return capacity;
    }

    public void setCapacity(long capacity) {
        this.capacity = capacity;
    }

    public double getRefillTokensPerSecond() {
        return refillTokensPerSecond;
    }

    public void setRefillTokensPerSecond(double refillTokensPerSecond) {
        this.refillTokensPerSecond = refillTokensPerSecond;
    }

    public String getClientHeader() {
        return clientHeader;
    }

    public void setClientHeader(String clientHeader) {
        this.clientHeader = clientHeader;
    }

    public Eviction getEviction() {
        return eviction;
    }

    /**
     * Buckets untouched for longer than {@code idleTtl} are removed by a sweep
     * that runs every {@code sweepInterval}. A removed client simply gets a
     * fresh, full bucket on its next request.
     */
    public static class Eviction {

        private boolean enabled = true;
        private Duration idleTtl = Duration.ofMinutes(10);
        private Duration sweepInterval = Duration.ofMinutes(1);

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public Duration getIdleTtl() {
            return idleTtl;
        }

        public void setIdleTtl(Duration idleTtl) {
            this.idleTtl = idleTtl;
        }

        public Duration getSweepInterval() {
            return sweepInterval;
        }

        public void setSweepInterval(Duration sweepInterval) {
            this.sweepInterval = sweepInterval;
        }
    }
}
