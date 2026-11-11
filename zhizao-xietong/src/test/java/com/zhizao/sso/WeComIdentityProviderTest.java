package com.zhizao.sso;

import com.zhizao.common.BadRequestException;
import com.zhizao.common.ForbiddenException;
import com.zhizao.config.SsoProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.hamcrest.Matchers.startsWith;

/**
 * 企微适配器：code → access_token → userid 的解析、openid 拒绝与缺配置启动失败。
 *
 * @author Caspian
 */
class WeComIdentityProviderTest {

    private MockRestServiceServer server;
    private WeComIdentityProvider provider;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        provider = new WeComIdentityProvider(properties(true), builder);
    }

    @Test
    void exchangesCodeForStableUserid() {
        expectToken();
        server.expect(requestTo(startsWith("https://qyapi.weixin.qq.com/cgi-bin/auth/getuserinfo")))
                .andRespond(withSuccess("{\"errcode\":0,\"userid\":\"u1001\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(startsWith("https://qyapi.weixin.qq.com/cgi-bin/user/get")))
                .andRespond(withSuccess("{\"errcode\":0,\"name\":\"张三\",\"mobile\":\"13800000000\","
                        + "\"email\":\"a@b.c\",\"department\":[1,2],\"status\":1}", MediaType.APPLICATION_JSON));

        IdentityUser identity = provider.exchange("code-1", "https://mes.example.com/sso/callback");

        assertEquals("WEIXIN_WORK", identity.provider());
        assertEquals("u1001", identity.externalId());
        assertEquals("张三", identity.displayName());
        assertEquals(2, identity.departmentIds().size());
        assertTrue(identity.active());
        server.verify();
    }

    @Test
    void rejectsExternalContactOpenId() {
        expectToken();
        server.expect(requestTo(startsWith("https://qyapi.weixin.qq.com/cgi-bin/auth/getuserinfo")))
                .andRespond(withSuccess("{\"errcode\":0,\"openid\":\"o-123\"}", MediaType.APPLICATION_JSON));

        assertThrows(ForbiddenException.class,
                () -> provider.exchange("code-2", "https://mes.example.com/sso/callback"));
    }

    @Test
    void rejectsExpiredOrReusedCode() {
        expectToken();
        server.expect(requestTo(startsWith("https://qyapi.weixin.qq.com/cgi-bin/auth/getuserinfo")))
                .andRespond(withSuccess("{\"errcode\":40029,\"errmsg\":\"invalid code\"}", MediaType.APPLICATION_JSON));

        assertThrows(BadRequestException.class,
                () -> provider.exchange("code-3", "https://mes.example.com/sso/callback"));
    }

    @Test
    void marksDeactivatedMemberInactive() {
        expectToken();
        server.expect(requestTo(startsWith("https://qyapi.weixin.qq.com/cgi-bin/auth/getuserinfo")))
                .andRespond(withSuccess("{\"errcode\":0,\"userid\":\"u1002\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(startsWith("https://qyapi.weixin.qq.com/cgi-bin/user/get")))
                .andRespond(withSuccess("{\"errcode\":0,\"name\":\"离职\",\"department\":[1],\"status\":4}",
                        MediaType.APPLICATION_JSON));

        IdentityUser identity = provider.exchange("code-4", "https://mes.example.com/sso/callback");

        assertEquals("u1002", identity.externalId());
        assertFalse(identity.active());
    }

    @Test
    void reusesCachedAccessToken() {
        expectToken();
        server.expect(requestTo(startsWith("https://qyapi.weixin.qq.com/cgi-bin/auth/getuserinfo")))
                .andRespond(withSuccess("{\"errcode\":0,\"userid\":\"u1\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(startsWith("https://qyapi.weixin.qq.com/cgi-bin/user/get")))
                .andRespond(withSuccess("{\"errcode\":0,\"name\":\"甲\",\"status\":1}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(startsWith("https://qyapi.weixin.qq.com/cgi-bin/auth/getuserinfo")))
                .andRespond(withSuccess("{\"errcode\":0,\"userid\":\"u1\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(startsWith("https://qyapi.weixin.qq.com/cgi-bin/user/get")))
                .andRespond(withSuccess("{\"errcode\":0,\"name\":\"甲\",\"status\":1}", MediaType.APPLICATION_JSON));

        provider.exchange("code-a", "https://mes.example.com/sso/callback");
        provider.exchange("code-b", "https://mes.example.com/sso/callback");

        server.verify();
    }

    @Test
    void failsFastWhenEnabledWithoutCredentials() {
        SsoProperties missingSecret = properties(true);
        missingSecret.getWecom().setSecret(null);

        assertThrows(IllegalStateException.class,
                () -> new WeComIdentityProvider(missingSecret, RestClient.builder()));
    }

    @Test
    void allowsBlankCredentialsWhenSwitchOff() {
        SsoProperties disabled = new SsoProperties();
        WeComIdentityProvider provider = new WeComIdentityProvider(disabled, RestClient.builder());

        assertEquals("WEIXIN_WORK", provider.provider());
    }

    private void expectToken() {
        server.expect(requestTo(startsWith("https://qyapi.weixin.qq.com/cgi-bin/gettoken")))
                .andRespond(withSuccess("{\"errcode\":0,\"access_token\":\"TOKEN\",\"expires_in\":7200}",
                        MediaType.APPLICATION_JSON));
    }

    private SsoProperties properties(boolean enabled) {
        SsoProperties properties = new SsoProperties();
        properties.setEnabled(enabled);
        properties.getWecom().setCorpId("corp-test");
        properties.getWecom().setAgentId("1000002");
        properties.getWecom().setSecret("secret-test");
        properties.getWecom().setRedirectUri("https://mes.example.com/sso/callback");
        return properties;
    }
}
