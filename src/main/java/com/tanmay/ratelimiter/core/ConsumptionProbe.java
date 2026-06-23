package com.tanmay.ratelimiter.core;

/**
 * Outcome of a single {@link TokenBucket#tryConsume(long)} call.
 *
 * @param allowed           whether the request may proceed
 * @param remainingTokens   whole tokens left in the bucket after this call
 * @param retryAfterMillis  if rejected, the wait until enough tokens accrue
 *                          (0 when allowed)
 */
public record ConsumptionProbe(boolean allowed, long remainingTokens, long retryAfterMillis) {

    public static ConsumptionProbe allowed(long remainingTokens) {
        return new ConsumptionProbe(true, remainingTokens, 0L);
    }

    public static ConsumptionProbe rejected(long remainingTokens, long retryAfterMillis) {
        return new ConsumptionProbe(false, remainingTokens, retryAfterMillis);
    }
}
