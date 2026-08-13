package carrental.chat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RateLimiterTest {

    @Test
    void allowsUpToMaxThenBlocks() {
        RateLimiter limiter = new RateLimiter();
        for (int i = 0; i < 3; i++) {
            assertTrue(limiter.tryAcquire("1.2.3.4"), "request " + i + " should be allowed");
        }
        assertFalse(limiter.tryAcquire("1.2.3.4"), "4th request must be blocked");
    }

    @Test
    void differentIpsAreIndependent() {
        RateLimiter limiter = new RateLimiter();
        assertTrue(limiter.tryAcquire("a"));
        assertTrue(limiter.tryAcquire("a"));
        assertTrue(limiter.tryAcquire("a"));
        assertFalse(limiter.tryAcquire("a"));
        assertTrue(limiter.tryAcquire("b"), "other IP must still have tokens");
    }
}