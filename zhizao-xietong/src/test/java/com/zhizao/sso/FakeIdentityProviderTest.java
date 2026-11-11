package com.zhizao.sso;

import com.zhizao.common.BadRequestException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 假身份提供方：code 到外部身份的确定性映射，以及非法 code 的明确拒绝。 */
class FakeIdentityProviderTest {

    private final FakeIdentityProvider provider = new FakeIdentityProvider();

    @Test
    void mapsCodeToActiveIdentity() {
        IdentityUser identity = provider.exchange("fake-u1001", "http://localhost/callback");

        assertEquals("FAKE", identity.provider());
        assertEquals("u1001", identity.externalId());
        assertTrue(identity.active());
        assertFalse(provider.exchange("fake-disabled-u1002", "http://localhost/callback").active());
    }

    @Test
    void rejectsMalformedCode() {
        assertThrows(BadRequestException.class, () -> provider.exchange("bad-code", "http://localhost/callback"));
        assertThrows(BadRequestException.class, () -> provider.exchange("fake-", "http://localhost/callback"));
    }

    @Test
    void authorizeUrlCarriesCodeAndState() {
        String url = provider.authorizeUrl("s1", "http://localhost/callback");

        assertTrue(url.contains("code=fake-dev-worker"));
        assertTrue(url.contains("state=s1"));
    }
}
