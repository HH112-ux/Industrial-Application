package com.zhizao.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JWT 密钥强度在启动期校验：过短或缺失时直接失败，不允许应用带弱密钥启动。
 *
 * @author Caspian
 */
class JwtUtilTest {

    private static final String MIN_LENGTH_SECRET = "a".repeat(32);

    @Test
    void rejectsSecretShorterThanThirtyTwoBytes() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> new JwtUtil("too-short-secret", 60_000));

        assertTrue(error.getMessage().contains("32"), error.getMessage());
    }

    @Test
    void rejectsMissingSecret() {
        assertThrows(IllegalStateException.class, () -> new JwtUtil(null, 60_000));
        assertThrows(IllegalStateException.class, () -> new JwtUtil("", 60_000));
    }

    @Test
    void acceptsSecretOfExactlyThirtyTwoBytes() {
        JwtUtil jwtUtil = new JwtUtil(MIN_LENGTH_SECRET, 60_000);

        String token = jwtUtil.generateToken("admin", "ADMIN", 3);

        assertTrue(jwtUtil.validate(token));
        assertEquals("admin", jwtUtil.getUsername(token));
        assertEquals("ADMIN", jwtUtil.getRole(token));
        assertEquals(3, jwtUtil.getTokenVersion(token));
    }
}
