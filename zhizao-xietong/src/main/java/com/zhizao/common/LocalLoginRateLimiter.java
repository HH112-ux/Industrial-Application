package com.zhizao.common;

import org.springframework.stereotype.Component;

import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.function.LongSupplier;

/**
 * 单实例本地登录限流：按用户名维护进程内滑动窗口。
 *
 * <p><strong>仅适用于单实例部署。</strong>多实例部署必须替换为共享存储实现（如 Redis），
 * 否则每个实例各自计数，整体阈值被放大；本实现不感知其他实例的失败次数。</p>
 *
 * @author Caspian
 */
@Component
public class LocalLoginRateLimiter implements LoginRateLimiter {

    private static final int MAX_ATTEMPTS = 10;
    private static final long WINDOW_MS = 60_000;

    private final Map<String, Deque<Long>> attempts = new ConcurrentHashMap<>();
    private final LongSupplier clock;

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
        String k = key == null || key.isBlank() ? "anonymous" : key;
        Deque<Long> times = attempts.computeIfAbsent(k, x -> new ConcurrentLinkedDeque<>());
        long now = clock.getAsLong();
        synchronized (times) {
            while (!times.isEmpty() && now - times.peekFirst() > WINDOW_MS) {
                times.pollFirst();
            }
            if (times.size() >= MAX_ATTEMPTS) {
                throw new BusinessException("登录尝试过于频繁，请稍后再试");
            }
            times.addLast(now);
        }
    }
}
