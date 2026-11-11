package com.zhizao.sso;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * 一次性 state 存储：服务端签发、绑定浏览器、消费即失效。
 *
 * <p>登录 CSRF 的防线是「绑定」而不是「随机」：state 经企微页面回到前端地址，攻击者可以
 * 把自己的 state + code 交给受害者，只有绑到受害者浏览器上不可读的 HttpOnly Cookie，
 * 攻击者才凑不出「属于受害者」的组合。</p>
 *
 * <p>回收口径与 {@link com.zhizao.common.LocalLoginRateLimiter} 一致：按间隔清扫过期项，
 * 并限制条目上限。单实例内存存储，多实例需换共享存储（与登录限流同一限制）。</p>
 *
 * @author Caspian
 */
@Component
public class SsoStateStore {

    /** 绑定浏览器会话的 Cookie 名，HttpOnly，前端 JS 读不到。 */
    public static final String BROWSER_COOKIE = "sso_browser";

    /** state 有效期：覆盖扫码与确认，超时按失败处理。 */
    static final long STATE_TTL_MS = 5 * 60_000;

    private static final int MAX_ENTRIES = 10_000;
    private static final long SWEEP_INTERVAL_MS = 60_000;
    private static final int TOKEN_BYTES = 32;

    private final Map<String, Entry> states = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();
    private final LongSupplier clock;
    private final Object sweepLock = new Object();
    private volatile long lastSweepAt;

    public SsoStateStore() {
        this(System::currentTimeMillis);
    }

    SsoStateStore(LongSupplier clock) {
        this.clock = clock;
    }

    /** 签发 state 并绑定浏览器标识，state 只进入企微授权地址。 */
    public String issue(String browserToken) {
        long now = clock.getAsLong();
        sweepIfDue(now);
        ensureCapacity(now);
        String state = randomToken();
        states.put(state, new Entry(browserToken, now + STATE_TTL_MS));
        return state;
    }

    /**
     * 消费 state：存在、未过期且浏览器标识一致才返回 true。
     *
     * <p>无论成败都先移除，因此重复回调必然失败；伪造、过期、复用走同一条拒绝路径。</p>
     *
     * @param state        回调带回的 state
     * @param browserToken 当前请求 Cookie 中的浏览器标识
     * @return true 表示校验通过且已消费
     */
    public boolean consume(String state, String browserToken) {
        long now = clock.getAsLong();
        sweepIfDue(now);
        if (state == null || state.isBlank() || browserToken == null || browserToken.isBlank()) {
            return false;
        }
        Entry entry = states.remove(state);
        if (entry == null || entry.expiresAt() <= now) {
            return false;
        }
        return MessageDigest.isEqual(entry.browserToken().getBytes(StandardCharsets.UTF_8),
                browserToken.getBytes(StandardCharsets.UTF_8));
    }

    public String newBrowserToken() {
        return randomToken();
    }

    int size() {
        return states.size();
    }

    private String randomToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private void sweepIfDue(long now) {
        if (now - lastSweepAt < SWEEP_INTERVAL_MS) {
            return;
        }
        synchronized (sweepLock) {
            if (now - lastSweepAt < SWEEP_INTERVAL_MS) {
                return;
            }
            lastSweepAt = now;
            states.entrySet().removeIf(entry -> entry.getValue().expiresAt() <= now);
        }
    }

    /** 被淘汰的只是未完成的登录，用户重扫一次即可，不会泄漏内存。 */
    private void ensureCapacity(long now) {
        if (states.size() < MAX_ENTRIES) {
            return;
        }
        states.entrySet().removeIf(entry -> entry.getValue().expiresAt() <= now);
        while (states.size() >= MAX_ENTRIES) {
            Map.Entry<String, Entry> earliest = states.entrySet().stream()
                    .min(Comparator.comparingLong(entry -> entry.getValue().expiresAt()))
                    .orElse(null);
            if (earliest == null) {
                return;
            }
            states.remove(earliest.getKey(), earliest.getValue());
        }
    }

    private record Entry(String browserToken, long expiresAt) {
    }
}
