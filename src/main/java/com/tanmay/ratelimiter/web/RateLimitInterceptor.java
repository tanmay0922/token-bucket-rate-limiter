package com.tanmay.ratelimiter.web;

import com.tanmay.ratelimiter.config.RateLimitProperties;
import com.tanmay.ratelimiter.core.ConsumptionProbe;
import com.tanmay.ratelimiter.core.RateLimiterService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

/**
 * Applies the token bucket to every incoming request before it reaches a
 * controller. On rejection it short-circuits with HTTP 429 and standard
 * rate-limit headers so clients can back off intelligently.
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimiterService rateLimiterService;
    private final RateLimitProperties properties;

    public RateLimitInterceptor(RateLimiterService rateLimiterService, RateLimitProperties properties) {
        this.rateLimiterService = rateLimiterService;
        this.properties = properties;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {

        String clientKey = resolveClientKey(request);
        ConsumptionProbe probe = rateLimiterService.tryAcquire(clientKey);

        response.setHeader("X-RateLimit-Limit", String.valueOf(properties.getCapacity()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(probe.remainingTokens()));

        if (probe.allowed()) {
            return true;
        }

        long retryAfterSeconds = (long) Math.ceil(probe.retryAfterMillis() / 1000.0);
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(String.format(
                "{\"error\":\"rate_limit_exceeded\",\"message\":\"Too many requests for '%s'. Retry after %d s.\",\"retryAfterSeconds\":%d}",
                clientKey, retryAfterSeconds, retryAfterSeconds));
        return false;
    }

    /** Identify the caller by configured header, else by remote address. */
    private String resolveClientKey(HttpServletRequest request) {
        String headerValue = request.getHeader(properties.getClientHeader());
        if (headerValue != null && !headerValue.isBlank()) {
            return headerValue;
        }
        return request.getRemoteAddr();
    }
}
