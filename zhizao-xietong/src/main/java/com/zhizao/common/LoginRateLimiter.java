package com.zhizao.common;

import org.springframework.stereotype.Component;

import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * 登录限流：按用户名维度，滑动窗口内限制尝试次数。
 */
@Component
public class LoginRateLimiter {

    private static final int MAX_ATTEMPTS = 10;
    private static final long WINDOW_MS = 60_000;

    private final Map<String, Deque<Long>> attempts = new ConcurrentHashMap<>();

    public void check(String key) {
        String k = key == null || key.isBlank() ? "anonymous" : key;
        Deque<Long> times = attempts.computeIfAbsent(k, x -> new ConcurrentLinkedDeque<>());
        long now = System.currentTimeMillis();
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
