package com.tanmay.ratelimiter.core;

/**
 * A single thread-safe token bucket.
 *
 * <p>The bucket holds up to {@code capacity} tokens and refills continuously at
 * {@code refillTokensPerSecond}. Refill is computed lazily: instead of a
 * background thread topping every bucket up on a timer, we record the timestamp
 * of the last refill and, on each access, add the tokens that have accrued since.
 * This keeps memory low (no scheduler per key) and is exact to nanosecond
 * resolution.
 *
 * <p>All mutating access goes through {@code synchronized} methods, so a bucket
 * is safe to share across request threads.
 */
public class TokenBucket {

    private final long capacity;
    private final double refillTokensPerSecond;

    /** Fractional tokens are kept so slow refill rates (e.g. 0.5/s) stay accurate. */
    private double availableTokens;
    private long lastRefillNanos;

    /** When this bucket was last touched — used by the eviction sweep. */
    private long lastAccessNanos;

    public TokenBucket(long capacity, double refillTokensPerSecond) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        if (refillTokensPerSecond <= 0) {
            throw new IllegalArgumentException("refillTokensPerSecond must be positive");
        }
        this.capacity = capacity;
        this.refillTokensPerSecond = refillTokensPerSecond;
        this.availableTokens = capacity; // start full
        long now = System.nanoTime();
        this.lastRefillNanos = now;
        this.lastAccessNanos = now;
    }

    /**
     * Attempt to take {@code tokens} from the bucket.
     *
     * @return a probe describing whether it succeeded, how many tokens remain,
     *         and (on failure) how long until enough tokens accrue.
     */
    public synchronized ConsumptionProbe tryConsume(long tokens) {
        if (tokens <= 0) {
            throw new IllegalArgumentException("tokens must be positive");
        }
        lastAccessNanos = System.nanoTime();
        refill();

        if (availableTokens >= tokens) {
            availableTokens -= tokens;
            return ConsumptionProbe.allowed(remainingWholeTokens());
        }

        double deficit = tokens - availableTokens;
        long retryAfterMillis = (long) Math.ceil(deficit / refillTokensPerSecond * 1000.0);
        return ConsumptionProbe.rejected(remainingWholeTokens(), retryAfterMillis);
    }

    /** Current whole tokens available, after accounting for accrued refill. */
    public synchronized long availableTokens() {
        refill();
        return remainingWholeTokens();
    }

    public long capacity() {
        return capacity;
    }

    /** Milliseconds since this bucket was last consumed from. */
    public synchronized long idleMillis() {
        return (System.nanoTime() - lastAccessNanos) / 1_000_000L;
    }

    /** Add tokens accrued since the last refill, capped at capacity. */
    private void refill() {
        long now = System.nanoTime();
        long elapsedNanos = now - lastRefillNanos;
        if (elapsedNanos <= 0) {
            return;
        }
        double accrued = (elapsedNanos / 1_000_000_000.0) * refillTokensPerSecond;
        if (accrued > 0) {
            availableTokens = Math.min(capacity, availableTokens + accrued);
            lastRefillNanos = now;
        }
    }

    private long remainingWholeTokens() {
        return (long) Math.floor(availableTokens);
    }
}
