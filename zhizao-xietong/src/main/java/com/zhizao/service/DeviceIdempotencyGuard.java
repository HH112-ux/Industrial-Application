package com.zhizao.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 设备上报幂等守卫：在进程内记录已处理的幂等键，重复上报直接短路。
 *
 * <p>当前实现有界且带过期时间，重启后清空；S2 引入 `eq_telemetry.ingest_key` 唯一索引后，
 * 持久化去重由数据库承担，本组件保留为接入层的快速短路。</p>
 *
 * @author Caspian
 */
@Component
public class DeviceIdempotencyGuard {

    private final Map<String, Long> seen = new ConcurrentHashMap<>();

    @Value("${device.idempotency.ttl-seconds:600}")
    private long ttlSeconds;
    @Value("${device.idempotency.max-entries:10000}")
    private int maxEntries;

    /**
     * 尝试登记一个幂等键。
     *
     * @param key 设备侧生成的唯一键，为空表示不做幂等控制
     * @return 首次出现返回 true；在过期窗口内重复出现返回 false
     */
    public boolean acquire(String key) {
        if (!StringUtils.hasText(key)) {
            return true;
        }
        long now = System.currentTimeMillis();
        evictIfNecessary(now);
        Long previous = seen.putIfAbsent(key, now);
        if (previous == null) {
            return true;
        }
        if (now - previous > ttlSeconds * 1000) {
            seen.put(key, now);
            return true;
        }
        return false;
    }

    private void evictIfNecessary(long now) {
        if (seen.size() < maxEntries) {
            return;
        }
        long expireBefore = now - ttlSeconds * 1000;
        seen.entrySet().removeIf(entry -> entry.getValue() < expireBefore);
        if (seen.size() >= maxEntries) {
            seen.clear();
        }
    }
}
