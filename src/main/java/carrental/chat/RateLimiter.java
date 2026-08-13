package carrental.chat;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Per-IP token-bucket rate limiter for the public chat endpoint.
 *
 * <p>Buckets refill slowly (1 token / 30s, max 3) to keep OpenRouter usage
 * bounded. Idle entries are evicted lazily on every call so the map
 * does not grow unbounded.
 */
@Component
public class RateLimiter {

    private static final int MAX_TOKENS = 3;
    private static final long REFILL_INTERVAL_MS = 30_000L; // 30 s per token

    private static final class Bucket {
        final AtomicInteger tokens = new AtomicInteger(MAX_TOKENS);
        volatile long lastRefillMs = Instant.now().toEpochMilli();
    }

    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    /**
     * @return {@code true} if the caller may proceed, {@code false} if the
     *         bucket is empty for this key.
     */
    public boolean tryAcquire(String key) {
        evictIdle();
        Bucket b = buckets.computeIfAbsent(key, k -> new Bucket());
        long now = Instant.now().toEpochMilli();
        long elapsed = now - b.lastRefillMs;
        if (elapsed > REFILL_INTERVAL_MS) {
            // refill at most (elapsed / interval) tokens, capped at MAX
            int refilled = (int) Math.min(MAX_TOKENS, elapsed / REFILL_INTERVAL_MS);
            if (refilled > 0) {
                b.tokens.set(Math.min(MAX_TOKENS, b.tokens.get() + refilled));
                b.lastRefillMs = now;
            }
        }
        return b.tokens.getAndUpdate(t -> t > 0 ? t - 1 : t) > 0;
    }

    /**
     * Periodically drop buckets that have been full for at least
     * {@code MAX_TOKENS * REFILL_INTERVAL_MS}. Cheap amortized sweep.
     */
    private void evictIdle() {
        if (buckets.size() < 256) return; // skip sweep when small
        long now = Instant.now().toEpochMilli();
        long cutoff = now - (long) MAX_TOKENS * REFILL_INTERVAL_MS;
        buckets.entrySet().removeIf(e -> e.getValue().lastRefillMs < cutoff);
    }
}