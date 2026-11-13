package com.zhizao.controller;

import com.zhizao.common.BadRequestException;
import com.zhizao.common.BusinessException;
import com.zhizao.common.ClientIpResolver;
import com.zhizao.common.ForbiddenException;
import com.zhizao.common.LoginRateLimiter;
import com.zhizao.common.Result;
import com.zhizao.dto.LoginRequest;
import com.zhizao.dto.LoginResponse;
import com.zhizao.dto.SsoLoginRequest;
import com.zhizao.entity.AuthEvent;
import com.zhizao.mapper.AuthEventMapper;
import com.zhizao.service.AuthAuditService;
import com.zhizao.service.AuthService;
import com.zhizao.sso.SsoAuthService;
import com.zhizao.sso.SsoStateStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 登录端点：限流、失败分类、登出与 SSO 换票都必须留痕，且审计失败不能影响登录。
 *
 * @author Caspian
 */
@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    private static final String IP = "127.0.0.1";
    private static final int IP_MAX_ATTEMPTS = AuthController.DEFAULT_IP_MAX_ATTEMPTS;
    private static final String PASSWORD = "PlainSecret!42";
    private static final String TICKET = "ticket-abc";
    private static final String STATE = "state-xyz";

    @Mock
    private AuthService authService;
    @Mock
    private SsoAuthService ssoAuthService;
    @Mock
    private LoginRateLimiter loginRateLimiter;
    @Mock
    private AuthAuditService authAuditService;

    /** 带可信代理的解析器：留空阈值时 IP 维度自动启用，作为默认测试上下文。 */
    private final ClientIpResolver clientIpResolver = new ClientIpResolver("10.0.0.10");

    private AuthController controller;

    private final MockHttpServletRequest request = new MockHttpServletRequest();

    @BeforeEach
    void setUp() {
        controller = new AuthController(authService, ssoAuthService, loginRateLimiter,
                authAuditService, clientIpResolver, true, "");
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private LoginResponse loginResponse() {
        return new LoginResponse("token-abc", "u1001", "张三", "USER", false);
    }

    @Test
    void loginFailuresAreClassifiedForAudit() {
        when(authService.login(any()))
                .thenThrow(new BusinessException("用户名或密码错误"))
                .thenThrow(new BusinessException(AuthService.SSO_LOGIN_HINT))
                .thenThrow(new ForbiddenException("账号已禁用"));

        assertThrows(BusinessException.class,
                () -> controller.login(new LoginRequest("u1001", PASSWORD), request));
        assertThrows(BusinessException.class,
                () -> controller.login(new LoginRequest("u1001", PASSWORD), request));
        assertThrows(ForbiddenException.class,
                () -> controller.login(new LoginRequest("u1001", PASSWORD), request));

        verify(loginRateLimiter, times(3)).check("u1001");
        verify(loginRateLimiter, times(3)).check("login-ip:127.0.0.1", IP_MAX_ATTEMPTS);
        verify(authAuditService).record(AuthAuditService.EVENT_LOGIN, "u1001", false,
                AuthAuditService.BAD_CREDENTIALS, "用户名或密码错误", IP);
        verify(authAuditService).record(AuthAuditService.EVENT_LOGIN, "u1001", false,
                AuthAuditService.SSO_REQUIRED, AuthService.SSO_LOGIN_HINT, IP);
        verify(authAuditService).record(AuthAuditService.EVENT_LOGIN, "u1001", false,
                AuthAuditService.ACCOUNT_DISABLED, "账号已禁用", IP);
    }

    @Test
    void loginSuccessRecordsSuccessForLocalAndSso() {
        when(authService.login(any())).thenReturn(loginResponse());
        when(ssoAuthService.login(TICKET, STATE, "browser-1", IP)).thenReturn(loginResponse());

        assertEquals("token-abc",
                controller.login(new LoginRequest("u1001", PASSWORD), request).getData().token());
        assertEquals("token-abc", controller.wecomLogin(
                new SsoLoginRequest(TICKET, STATE), "browser-1", request).getData().token());

        verify(authAuditService).record(AuthAuditService.EVENT_LOGIN, "u1001", true,
                AuthAuditService.SUCCESS, null, IP);
        verify(authAuditService).record(AuthAuditService.EVENT_SSO_LOGIN, "u1001", true,
                AuthAuditService.SUCCESS, null, IP);
    }

    @Test
    void loginStopsBeforeServiceWhenRateLimited() {
        doThrow(new BusinessException("登录尝试过于频繁，请稍后再试")).when(loginRateLimiter)
                .check("u1001");

        assertThrows(BusinessException.class,
                () -> controller.login(new LoginRequest("u1001", PASSWORD), request));

        verify(authService, never()).login(any());
        verify(authAuditService).record(AuthAuditService.EVENT_LOGIN, "u1001", false,
                AuthAuditService.RATE_LIMITED, "登录尝试过于频繁，请稍后再试", IP);
    }

    @Test
    void loginStopsWhenIpDimensionRateLimited() {
        doNothing().when(loginRateLimiter).check("u1001");
        doThrow(new BusinessException("登录尝试过于频繁，请稍后再试")).when(loginRateLimiter)
                .check("login-ip:127.0.0.1", IP_MAX_ATTEMPTS);

        assertThrows(BusinessException.class,
                () -> controller.login(new LoginRequest("u1001", PASSWORD), request));

        verify(authService, never()).login(any());
        verify(authAuditService).record(AuthAuditService.EVENT_LOGIN, "u1001", false,
                AuthAuditService.RATE_LIMITED, "登录尝试过于频繁，请稍后再试", IP);
    }

    @Test
    void loginRateLimitsAccountAndIpSeparately() {
        MockHttpServletRequest otherClient = new MockHttpServletRequest();
        otherClient.setRemoteAddr("10.0.0.5");
        when(authService.login(any())).thenReturn(loginResponse());

        controller.login(new LoginRequest("u1001", PASSWORD), request);
        controller.login(new LoginRequest("u1001", PASSWORD), otherClient);
        controller.login(new LoginRequest("u1002", PASSWORD), request);

        verify(loginRateLimiter, times(2)).check("u1001");
        verify(loginRateLimiter).check("u1002");
        verify(loginRateLimiter, times(2)).check("login-ip:127.0.0.1", IP_MAX_ATTEMPTS);
        verify(loginRateLimiter).check("login-ip:10.0.0.5", IP_MAX_ATTEMPTS);
    }

    @Test
    void ipDimensionDisabledWhenTrustedProxiesMissing() {
        AuthController directController = new AuthController(authService, ssoAuthService,
                loginRateLimiter, authAuditService, new ClientIpResolver(""), true, "");
        when(authService.login(any())).thenReturn(loginResponse());

        directController.login(new LoginRequest("u1001", PASSWORD), request);

        verify(loginRateLimiter).check("u1001");
        verify(loginRateLimiter, never()).check(anyString(), anyInt());
    }

    @Test
    void ipDimensionEnabledAutomaticallyWithTrustedProxies() {
        when(authService.login(any())).thenReturn(loginResponse());

        controller.login(new LoginRequest("u1001", PASSWORD), request);

        verify(loginRateLimiter).check("login-ip:127.0.0.1", IP_MAX_ATTEMPTS);
    }

    @Test
    void ipDimensionCanBeEnabledForDirectDeployment() {
        AuthController directController = new AuthController(authService, ssoAuthService,
                loginRateLimiter, authAuditService, new ClientIpResolver(""), true, "45");
        when(authService.login(any())).thenReturn(loginResponse());

        directController.login(new LoginRequest("u1001", PASSWORD), request);

        verify(loginRateLimiter).check("login-ip:127.0.0.1", 45);
    }

    @Test
    void ipDimensionCanBeDisabledExplicitly() {
        AuthController controllerWithoutIpLimit = new AuthController(authService, ssoAuthService,
                loginRateLimiter, authAuditService, clientIpResolver, true, "0");
        when(authService.login(any())).thenReturn(loginResponse());

        controllerWithoutIpLimit.login(new LoginRequest("u1001", PASSWORD), request);

        verify(loginRateLimiter).check("u1001");
        verify(loginRateLimiter, never()).check(anyString(), anyInt());
    }

    @Test
    void ipMaxAttemptsRejectsInvalidValues() {
        assertThrows(IllegalArgumentException.class,
                () -> AuthController.resolveIpMaxAttempts("abc", true));
        assertThrows(IllegalArgumentException.class,
                () -> AuthController.resolveIpMaxAttempts("-1", true));
        assertEquals(0, AuthController.resolveIpMaxAttempts("", false));
        assertEquals(IP_MAX_ATTEMPTS, AuthController.resolveIpMaxAttempts(" ", true));
        assertEquals(15, AuthController.resolveIpMaxAttempts("15", false));
    }

    @Test
    void logoutRecordsAuthenticatedUser() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("operator", null, List.of()));

        controller.logout(request);

        verify(authAuditService).record(AuthAuditService.EVENT_LOGOUT, "operator", true,
                AuthAuditService.SUCCESS, null, IP);
    }

    @Test
    void ssoFailuresAreClassifiedForAudit() {
        when(ssoAuthService.login(anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new BadRequestException("登录会话已失效，请重新扫码登录"))
                .thenThrow(new BadRequestException("企业微信授权码无效或已过期"))
                .thenThrow(new ForbiddenException("账号已被禁用"))
                .thenThrow(new ForbiddenException("仅限本企业成员登录"))
                .thenThrow(new BusinessException(503, "企业微信登录未启用"));

        for (int i = 0; i < 5; i++) {
            assertThrows(RuntimeException.class,
                    () -> controller.wecomLogin(new SsoLoginRequest(TICKET, STATE), "browser-1", request));
        }

        verify(authAuditService).record(AuthAuditService.EVENT_SSO_LOGIN, null, false,
                AuthAuditService.INVALID_STATE, "登录会话已失效，请重新扫码登录", IP);
        verify(authAuditService).record(AuthAuditService.EVENT_SSO_LOGIN, null, false,
                AuthAuditService.INVALID_TICKET, "企业微信授权码无效或已过期", IP);
        verify(authAuditService).record(AuthAuditService.EVENT_SSO_LOGIN, null, false,
                AuthAuditService.ACCOUNT_DISABLED, "账号已被禁用", IP);
        verify(authAuditService).record(AuthAuditService.EVENT_SSO_LOGIN, null, false,
                AuthAuditService.SSO_REJECTED, "仅限本企业成员登录", IP);
        verify(authAuditService).record(AuthAuditService.EVENT_SSO_LOGIN, null, false,
                AuthAuditService.SSO_DISABLED, "企业微信登录未启用", IP);
    }

    @Test
    void auditWriteFailureDoesNotBreakLogin() {
        AuthEventMapper failing = mock(AuthEventMapper.class);
        when(failing.insert(any(AuthEvent.class))).thenThrow(new IllegalStateException("db down"));
        AuthController controllerWithRealAudit = new AuthController(authService, ssoAuthService,
                loginRateLimiter, new AuthAuditService(failing, transactionManager()), clientIpResolver,
                true, "");
        when(authService.login(any())).thenReturn(loginResponse());

        Result<LoginResponse> result = controllerWithRealAudit.login(
                new LoginRequest("u1001", PASSWORD), request);

        assertEquals("token-abc", result.getData().token());
    }

    @Test
    void auditRowsNeverContainCredentialsOrSsoSecrets() {
        List<AuthEvent> rows = new ArrayList<>();
        AuthEventMapper capturing = mock(AuthEventMapper.class);
        when(capturing.insert(any(AuthEvent.class))).thenAnswer(invocation -> {
            rows.add(invocation.getArgument(0));
            return 1;
        });
        AuthController controllerWithRealAudit = new AuthController(authService, ssoAuthService,
                loginRateLimiter, new AuthAuditService(capturing, transactionManager()), clientIpResolver,
                true, "");
        when(authService.login(any())).thenReturn(loginResponse());
        when(ssoAuthService.login(anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new BadRequestException("登录会话已失效，请重新扫码登录"));

        controllerWithRealAudit.login(new LoginRequest("u1001", PASSWORD), request);
        assertThrows(BadRequestException.class, () -> controllerWithRealAudit.wecomLogin(
                new SsoLoginRequest(TICKET, STATE), "browser-1", request));

        StringBuilder dump = new StringBuilder();
        rows.forEach(row -> dump.append(row.getEventType()).append(row.getUsername())
                .append(row.getResultCode()).append(row.getReason()).append(row.getIp()));
        assertFalse(dump.toString().contains(PASSWORD));
        assertFalse(dump.toString().contains(TICKET));
        assertFalse(dump.toString().contains(STATE));
    }

    @Test
    void authorizeRedirectIssuesBrowserBindingCookie() {
        when(ssoAuthService.authorize(any())).thenReturn(
                new SsoAuthService.AuthorizeRedirect("https://open.work.weixin.qq.com/connect/qr", "browser-1"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        ResponseEntity<Void> result = controller.wecomAuthorize(null, response);

        assertEquals(HttpStatus.FOUND, result.getStatusCode());
        assertEquals("https://open.work.weixin.qq.com/connect/qr", result.getHeaders().getLocation().toString());
        String cookie = response.getHeader(HttpHeaders.SET_COOKIE);
        assertTrue(cookie.contains(SsoStateStore.BROWSER_COOKIE + "=browser-1"));
        assertTrue(cookie.contains("HttpOnly"));
        assertTrue(cookie.contains("Secure"));
        assertTrue(cookie.contains("SameSite=Lax"));
    }

    @Test
    void auditIpIgnoresForwardedHeaderFromUntrustedClient() {
        MockHttpServletRequest spoofed = new MockHttpServletRequest();
        spoofed.setRemoteAddr("10.0.0.7");
        spoofed.addHeader("X-Forwarded-For", "203.0.113.9");
        when(authService.login(any())).thenReturn(loginResponse());

        controller.login(new LoginRequest("u1001", PASSWORD), spoofed);

        verify(authAuditService).record(AuthAuditService.EVENT_LOGIN, "u1001", true,
                AuthAuditService.SUCCESS, null, "10.0.0.7");
    }

    private static PlatformTransactionManager transactionManager() {
        PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any(TransactionDefinition.class)))
                .thenReturn(new SimpleTransactionStatus());
        return manager;
    }
}
