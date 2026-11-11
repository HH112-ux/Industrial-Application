package com.zhizao.sso;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 一次性 state：绑定浏览器、过期即失效、消费一次、过期条目被清扫。
 *
 * @author Caspian
 */
class SsoStateStoreTest {

    private static final long FIVE_MINUTES = 5 * 60_000L;

    private final AtomicLong now = new AtomicLong(1_000_000L);
    private final SsoStateStore store = new SsoStateStore(now::get);

    @Test
    void issuedStateIsConsumedExactlyOnce() {
        String state = store.issue("browser-1");

        assertTrue(store.consume(state, "browser-1"));
        assertFalse(store.consume(state, "browser-1"), "重复回调必须失败");
    }

    @Test
    void forgedStateIsRejected() {
        assertFalse(store.consume("forged-state", "browser-1"));
    }

    @Test
    void expiredStateIsRejected() {
        String state = store.issue("browser-1");
        now.addAndGet(FIVE_MINUTES + 1);

        assertFalse(store.consume(state, "browser-1"));
    }

    @Test
    void expiredEntriesAreSweptOnNextIssue() {
        store.issue("browser-1");
        store.issue("browser-1");
        store.issue("browser-1");
        now.addAndGet(FIVE_MINUTES + 1);

        store.issue("browser-1");

        assertEquals(1, store.size());
    }
}
