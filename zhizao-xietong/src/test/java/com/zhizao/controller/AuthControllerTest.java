package com.zhizao.controller;

import com.zhizao.common.BusinessException;
import com.zhizao.common.LoginRateLimiter;
import com.zhizao.dto.LoginRequest;
import com.zhizao.service.AuthService;
import com.zhizao.sso.SsoAuthService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 登录端点：限流必须在业务分支之前计数，SSO 提示分支不能绕过。
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
}
