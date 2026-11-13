package com.zhizao.common;

import org.springframework.stereotype.Component;

import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.function.LongSupplier;

/**
 * 单实例本地登录限流：按键维护进程内滑动窗口。
 *
 * <p><strong>仅适用于单实例部署。</strong>多实例部署必须替换为共享存储实现（如 Redis），
 * 否则每个实例各自计数，整体阈值被放大；本实现不感知其他实例的失败次数。</p>
 *
 * <p>键会回收：每个窗口周期做一次过期清扫，连续无人尝试的键不会常驻；同时限制最大键数，
 * 超过上限时优先清掉窗口已过期的键，仍超限则淘汰「最后一次尝试最早」的键，
 * 避免随机用户名把内存涨爆。</p>
 *
 * @author Caspian
 */
@Component
public class LocalLoginRateLimiter implements LoginRateLimiter {

    private static final int MAX_ATTEMPTS = 10;
    private static final long WINDOW_MS = 60_000;
    private static final int MAX_KEYS = 10_000;

    private final Map<String, Deque<Long>> attempts = new ConcurrentHashMap<>();
    private final LongSupplier clock;
    private final Object sweepLock = new Object();
    private volatile long lastSweepAt;

    /**
     * 使用系统时钟构造本地限流器。
     */
    public LocalLoginRateLimiter() {
        this(System::currentTimeMillis);
    }

    /**
     * 使用指定时钟构造，便于测试窗口滑动。
     *
     * @param clock 毫秒时钟
     */
    LocalLoginRateLimiter(LongSupplier clock) {
        this.clock = clock;
    }

    @Override
    public void check(String key) {
        check(key, MAX_ATTEMPTS);
    }

    @Override
    public void check(String key, int maxAttempts) {
        String k = key == null || key.isBlank() ? "anonymous" : key;
        long now = clock.getAsLong();
        sweepIfDue(now);
        ensureCapacity(now);
        Deque<Long> times = attempts.computeIfAbsent(k, x -> new ConcurrentLinkedDeque<>());
        synchronized (times) {
            while (!times.isEmpty() && now - times.peekFirst() > WINDOW_MS) {
                times.pollFirst();
            }
            if (times.size() >= maxAttempts) {
                throw new BusinessException("登录尝试过于频繁，请稍后再试");
            }
            times.addLast(now);
        }
    }

    /**
     * 当前跟踪的键数量，供测试断言上限与回收。
     *
     * @return 键数量
     */
    int keyCount() {
        return attempts.size();
    }

    private void sweepIfDue(long now) {
        if (now - lastSweepAt < WINDOW_MS) {
            return;
        }
        synchronized (sweepLock) {
            if (now - lastSweepAt < WINDOW_MS) {
                return;
            }
            lastSweepAt = now;
            attempts.forEach((key, times) -> prune(key, times, now));
        }
    }

    private void ensureCapacity(long now) {
        if (attempts.size() < MAX_KEYS) {
            return;
        }
        attempts.forEach((key, times) -> prune(key, times, now));
        while (attempts.size() >= MAX_KEYS) {
            Map.Entry<String, Deque<Long>> oldest = attempts.entrySet().stream()
                    .min((left, right) -> Long.compare(lastAttempt(left.getValue()),
                            lastAttempt(right.getValue())))
                    .orElse(null);
            if (oldest == null) {
                return;
            }
            attempts.remove(oldest.getKey(), oldest.getValue());
        }
    }

    private void prune(String key, Deque<Long> times, long now) {
        synchronized (times) {
            while (!times.isEmpty() && now - times.peekFirst() > WINDOW_MS) {
                times.pollFirst();
            }
            if (times.isEmpty()) {
                attempts.remove(key, times);
            }
        }
    }

    private long lastAttempt(Deque<Long> times) {
        synchronized (times) {
            Long last = times.peekLast();
            return last == null ? Long.MIN_VALUE : last;
        }
    }
}
