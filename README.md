# Token Bucket Rate Limiter Service

An in-memory, thread-safe **token bucket** rate limiter built with Spring Boot 3
and Java 17. Every client gets its own bucket; requests over the limit are
rejected with HTTP `429 Too Many Requests` and standard rate-limit headers.

## The algorithm

A token bucket holds up to `capacity` tokens and refills at a steady
`refillTokensPerSecond`. Each request spends one token:

- **Tokens available** → request allowed, one token removed.
- **Bucket empty** → request rejected with a `Retry-After`.

`capacity` controls the **burst** a client may make instantly; the refill rate
controls the **sustained** throughput. This implementation refills *lazily* — it
records the last-access time and adds accrued tokens on the next request, so
there is no background thread per client.

```
capacity = 10, refill = 5/sec
 ┌────────────┐   take 1 token per request
 │ ●●●●●●●●●● │ ← refills +5 every second, capped at 10
 └────────────┘
```

## Run it

```bash
cd token-bucket-rate-limiter
mvn spring-boot:run
```

Then hammer the demo endpoint (default: burst 10, then 5/sec):

```bash
# allowed until the bucket drains, then 429s
for i in $(seq 1 15); do curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/api/ping; done
```

Identify a client with the `X-API-Key` header (otherwise the remote IP is used):

```bash
curl -i -H "X-API-Key: alice" http://localhost:8080/api/ping
```

Response headers on every call:

| Header | Meaning |
|---|---|
| `X-RateLimit-Limit` | Bucket capacity |
| `X-RateLimit-Remaining` | Whole tokens left |
| `Retry-After` | Seconds to wait (only on 429) |

## Configuration

Edit `src/main/resources/application.yml`:

```yaml
ratelimit:
  capacity: 10                 # max burst
  refill-tokens-per-second: 5  # sustained rate
  client-header: X-API-Key     # how clients are identified
  eviction:
    enabled: true              # set false to disable the cleanup sweep
    idle-ttl: 10m              # a client idle this long loses its bucket
    sweep-interval: 1m         # how often the cleanup runs
```

## Layout

| Class | Role |
|---|---|
| `core/TokenBucket` | The algorithm — thread-safe, lazy refill |
| `core/ConsumptionProbe` | Result of a consume attempt (allowed / remaining / retry-after) |
| `core/RateLimiterService` | One bucket per client key (`ConcurrentHashMap`) + idle eviction |
| `core/BucketEvictionScheduler` | Scheduled sweep that drops idle buckets to bound memory |
| `web/RateLimitInterceptor` | Enforces the limit, writes 429 + headers |
| `web/WebConfig` | Registers the interceptor on `/api/**` |
| `web/DemoController` | `/api/ping`, `/api/status` for testing |

## Tests

```bash
mvn test
```

Covers burst behaviour, refill over time, the capacity ceiling, per-client
isolation, idle-bucket eviction (and that an evicted client returns with a fresh
full bucket), and a concurrency stress test proving no over-granting under 16
threads racing on one bucket.

## Notes & extensions

- **Single node:** buckets live in process memory, so each instance limits
  independently. To share limits across instances, move the bucket state to
  Redis with an atomic Lua refill script (the `RateLimiterService` interface is
  the seam to swap).
- **Idle cleanup (implemented):** a scheduled sweep (`BucketEvictionScheduler`)
  drops buckets unused beyond `idle-ttl`, so memory stays bounded under high
  client cardinality. An evicted client transparently gets a fresh full bucket
  on its next request.
