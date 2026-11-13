package com.zhizao.common;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 客户端 IP 解析：默认不采信转发头，仅在可信代理后解析且只接受合法 IP 字面量。
 *
 * @author Caspian
 */
class ClientIpResolverTest {

    private static MockHttpServletRequest request(String remoteAddr, String forwardedFor) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remoteAddr);
        if (forwardedFor != null) {
            request.addHeader("X-Forwarded-For", forwardedFor);
        }
        return request;
    }

    @Test
    void ignoresForwardedHeaderWhenNoProxyConfigured() {
        ClientIpResolver resolver = new ClientIpResolver("");

        assertEquals("10.0.0.7", resolver.resolve(request("10.0.0.7", "203.0.113.9")));
    }

    @Test
    void ignoresForwardedHeaderWhenPeerIsNotTrustedProxy() {
        ClientIpResolver resolver = new ClientIpResolver("10.0.0.1");

        assertEquals("10.0.0.7", resolver.resolve(request("10.0.0.7", "203.0.113.9")));
    }

    @Test
    void trustsRightmostUntrustedHopFromTrustedProxy() {
        ClientIpResolver resolver = new ClientIpResolver("10.0.0.1, 10.0.0.2");

        assertEquals("203.0.113.9",
                resolver.resolve(request("10.0.0.2", "203.0.113.9, 10.0.0.1")));
    }

    @Test
    void ignoresClientSuppliedForwardedPrefix() {
        ClientIpResolver resolver = new ClientIpResolver("10.0.0.1");

        assertEquals("203.0.113.9",
                resolver.resolve(request("10.0.0.1", "6.6.6.6, 203.0.113.9")));
    }

    @Test
    void acceptsIpv6HopFromTrustedProxy() {
        ClientIpResolver resolver = new ClientIpResolver("::1");

        assertEquals("2001:db8::1", resolver.resolve(request("::1", "2001:db8::1")));
    }

    @Test
    void rejectsMalformedIpv6WithoutNameResolution() {
        ClientIpResolver resolver = new ClientIpResolver("10.0.0.1");

        assertEquals("10.0.0.1", resolver.resolve(request("10.0.0.1", "1:2:3")));
        assertEquals("10.0.0.1", resolver.resolve(request("10.0.0.1", "fe80::1%en0")));
    }

    @Test
    void fallsBackToPeerWhenForwardedValueIsNotIpLiteral() {
        ClientIpResolver resolver = new ClientIpResolver("10.0.0.1");

        assertEquals("10.0.0.1", resolver.resolve(request("10.0.0.1", "evil.example.com")));
        assertEquals("10.0.0.1", resolver.resolve(request("10.0.0.1", "999.1.1.1")));
    }

    @Test
    void fallsBackToPeerWhenTrustedProxySendsNoForwardedHeader() {
        ClientIpResolver resolver = new ClientIpResolver("10.0.0.1");

        assertEquals("10.0.0.1", resolver.resolve(request("10.0.0.1", null)));
        assertNull(resolver.resolve(null));
    }

    @Test
    void toleratesMissingRemoteAddress() {
        ClientIpResolver resolver = new ClientIpResolver("10.0.0.1");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(null);

        assertNull(resolver.resolve(request));
    }
}
