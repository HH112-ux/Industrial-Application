package com.zhizao.controller;

import com.zhizao.common.BusinessException;
import com.zhizao.common.LoginRateLimiter;
import com.zhizao.dto.LoginRequest;
import com.zhizao.service.AuthService;
import com.zhizao.sso.SsoAuthService;
import com.zhizao.sso.SsoStateStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 登录端点：限流必须在业务分支之前计数，SSO 提示分支不能绕过；授权跳转必须下发浏览器绑定 Cookie。
 *
 * @author Caspian
 */
@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;
    @Mock
    private SsoAuthService ssoAuthService;
    @Mock
    private LoginRateLimiter loginRateLimiter;

    @InjectMocks
    private AuthController controller;

    @Test
    void loginCountsRateLimitEvenWhenSsoHintIsReturned() {
        when(authService.login(any())).thenThrow(new BusinessException("该账号需使用企业微信扫码登录"));

        assertThrows(BusinessException.class,
                () -> controller.login(new LoginRequest("u1001", "any-password")));

        verify(loginRateLimiter).check("u1001");
    }

    @Test
    void loginStopsBeforeServiceWhenRateLimited() {
        doThrow(new BusinessException("登录过于频繁")).when(loginRateLimiter).check("u1001");

        assertThrows(BusinessException.class,
                () -> controller.login(new LoginRequest("u1001", "any-password")));

        verify(authService, never()).login(any());
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
        assertTrue(cookie.contains("SameSite=Lax"));
    }
}
