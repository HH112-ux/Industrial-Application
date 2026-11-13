package com.zhizao.common;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 客户端 IP 解析：默认不采信转发头，仅在可信代理后解析且只接受合法 IP 字面量。
 *
 * @author Caspian
 */
@ExtendWith(OutputCaptureExtension.class)
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
    void warnsOnceWhenForwardedHeaderIgnoredWithoutTrustedProxy(CapturedOutput output) {
        ClientIpResolver resolver = new ClientIpResolver("");

        assertEquals("10.0.0.7", resolver.resolve(request("10.0.0.7", "203.0.113.9")));
        assertEquals("10.0.0.7", resolver.resolve(request("10.0.0.7", "203.0.113.10")));

        String warning = "收到 X-Forwarded-For 但 TRUSTED_PROXIES 未配置";
        assertEquals(1, output.getOut().split(warning, -1).length - 1,
                "代理后未配置可信代理时应告警一次，且不改变 fail-closed 解析结果");
    }

    @Test
    void ignoresForwardedHeaderWhenPeerIsNotTrustedProxy() {
        ClientIpResolver resolver = new ClientIpResolver("10.0.0.1");

        assertEquals("10.0.0.7", resolver.resolve(request("10.0.0.7", "203.0.113.9")));
    }

    @Test
    void warnsOnceWhenUntrustedPeerSendsForwardedHeader(CapturedOutput output) {
        ClientIpResolver resolver = new ClientIpResolver("172.18.0.10");

        assertEquals("10.0.0.7", resolver.resolve(request("10.0.0.7", "203.0.113.9")));
        assertEquals("10.0.0.8", resolver.resolve(request("10.0.0.8", "203.0.113.10")));

        String warning = "X-Forwarded-For 来自未受信的 TCP 对端";
        assertEquals(1, output.getOut().split(warning, -1).length - 1,
                "配置写错时应告警一次，且不改变 fail-closed 解析结果");
        assertTrue(output.getOut().contains("10.0.0.7"), "告警需给出实际对端地址，便于定位");
        assertTrue(output.getOut().contains("至今未匹配到任何真实对端"),
                "从未命中过配置地址时需明确指出配置可能失效");
        assertTrue(output.getOut().contains("172.18.0.10"), "告警需回显当前配置值");
    }

    @Test
    void doesNotWarnWhenTrustedProxyIsActuallyHit(CapturedOutput output) {
        ClientIpResolver resolver = new ClientIpResolver("172.18.0.10");

        assertEquals("203.0.113.9", resolver.resolve(request("172.18.0.10", "203.0.113.9")));

        assertFalse(output.getOut().contains("未受信的 TCP 对端"),
                "可信代理命中时不应告警，避免掩盖真实误配");
    }

    @Test
    void rejectsInvalidTrustedProxyConfigurationAtStartup() {
        assertThrows(IllegalArgumentException.class, () -> new ClientIpResolver("172.18.0.0/16"));
        assertThrows(IllegalArgumentException.class, () -> new ClientIpResolver("frontend"));
        assertThrows(IllegalArgumentException.class, () -> new ClientIpResolver("999.1.1.1"));
        assertThrows(IllegalArgumentException.class, () -> new ClientIpResolver("10.0.0.1,0.0.0.0"));
        assertThrows(IllegalArgumentException.class, () -> new ClientIpResolver("0:0:0:0:0:0:0:0"),
                "IPv6 未指定地址的展开写法同样永不匹配，应在启动期拒绝");
    }

    @Test
    void acceptsIpv6TrustedProxyConfiguration() {
        assertEquals("2001:db8::1", new ClientIpResolver("::1, 2001:db8::2")
                .resolve(request("2001:db8::2", "2001:db8::1")));
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
