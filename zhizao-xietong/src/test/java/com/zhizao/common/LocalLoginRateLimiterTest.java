package com.zhizao.common;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void reclaimsKeysOnceTheirWindowHasSlid() {
        AtomicLong now = new AtomicLong(1_000_000L);
        LocalLoginRateLimiter limiter = new LocalLoginRateLimiter(now::get);

        for (int i = 0; i < 100; i++) {
            limiter.check("user-" + i);
        }
        assertEquals(100, limiter.keyCount());

        now.addAndGet(60_001L);
        limiter.check("someone");

        assertEquals(1, limiter.keyCount(), "过期键应在下一次清扫时回收");
    }

    @Test
    void keepsTrackedKeysBoundedUnderRandomUsernames() {
        AtomicLong now = new AtomicLong(1_000_000L);
        LocalLoginRateLimiter limiter = new LocalLoginRateLimiter(now::get);

        for (int i = 0; i < 10_050; i++) {
            limiter.check("random-user-" + i);
        }

        assertTrue(limiter.keyCount() <= 10_000, "随机用户名不能无限撑大内存");
    }
}
