package com.zhizao.common;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LocalLoginRateLimiterTest {

    @Test
    void rejectsEleventhAttemptWithinOneMinute() {
        AtomicLong now = new AtomicLong(1_000_000L);
        LocalLoginRateLimiter limiter = new LocalLoginRateLimiter(now::get);

        for (int i = 0; i < 10; i++) {
            assertDoesNotThrow(() -> limiter.check("zhangsan"));
        }
        assertThrows(BusinessException.class, () -> limiter.check("zhangsan"));
    }

    @Test
    void allowsAttemptsAgainAfterWindowSlides() {
        AtomicLong now = new AtomicLong(1_000_000L);
        LocalLoginRateLimiter limiter = new LocalLoginRateLimiter(now::get);

        for (int i = 0; i < 10; i++) {
            limiter.check("zhangsan");
        }
        now.addAndGet(60_001L);

        assertDoesNotThrow(() -> limiter.check("zhangsan"));
    }

    @Test
    void countsUsersIndependently() {
        AtomicLong now = new AtomicLong(1_000_000L);
        LocalLoginRateLimiter limiter = new LocalLoginRateLimiter(now::get);

        for (int i = 0; i < 10; i++) {
            limiter.check("zhangsan");
        }

        assertDoesNotThrow(() -> limiter.check("lisi"));
    }
}
