package com.zhizao.service;

import com.zhizao.common.BadRequestException;
import com.zhizao.common.BusinessException;
import com.zhizao.common.ForbiddenException;
import com.zhizao.common.UnauthorizedException;
import com.zhizao.config.SsoProperties;
import com.zhizao.dto.LoginRequest;
import com.zhizao.dto.LoginResponse;
import com.zhizao.dto.PasswordChangeRequest;
import com.zhizao.entity.SysUser;
import com.zhizao.mapper.SysUserMapper;
import com.zhizao.security.JwtUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private SysUserMapper userMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private SsoProperties ssoProperties;

    @InjectMocks
    private AuthService authService;

    private SysUser user(int enabled, int tokenVersion) {
        SysUser user = new SysUser();
        user.setId(1L);
        user.setUsername("admin");
        user.setPassword("encoded");
        user.setNickname("系统管理员");
        user.setRole("ADMIN");
        user.setEnabled(enabled);
        user.setTokenVersion(tokenVersion);
        return user;
    }

    @Test
    void loginSuccess() {
        when(userMapper.selectOne(any())).thenReturn(user(1, 2));
        when(passwordEncoder.matches("admin123", "encoded")).thenReturn(true);
        when(jwtUtil.generateToken("admin", "ADMIN", 2)).thenReturn("token-abc");

        LoginResponse resp = authService.login(new LoginRequest("admin", "admin123"));

        assertEquals("token-abc", resp.token());
        assertEquals("admin", resp.username());
        assertEquals("系统管理员", resp.nickname());
        assertEquals("ADMIN", resp.role());
    }

    @Test
    void loginWrongPasswordThrows() {
        when(userMapper.selectOne(any())).thenReturn(user(1, 0));
        when(passwordEncoder.matches("wrong", "encoded")).thenReturn(false);

        assertThrows(BusinessException.class,
                () -> authService.login(new LoginRequest("admin", "wrong")));
    }

    @Test
    void loginUnknownUserThrows() {
        when(userMapper.selectOne(any())).thenReturn(null);

        BusinessException error = assertThrows(BusinessException.class,
                () -> authService.login(new LoginRequest("nobody", "x")));

        assertEquals("用户名或密码错误", error.getMessage());
    }

    @Test
    void loginOnSsoAccountAsksForSsoLogin() {
        SysUser sso = user(1, 0);
        sso.setAuthSource("SSO");
        when(userMapper.selectOne(any())).thenReturn(sso);
        when(ssoProperties.isEnabled()).thenReturn(true);

        BusinessException error = assertThrows(BusinessException.class,
                () -> authService.login(new LoginRequest("u1001", "any-password")));

        assertEquals(AuthService.SSO_LOGIN_HINT, error.getMessage());
        verify(passwordEncoder, never()).matches(anyString(), anyString());
        verify(jwtUtil, never()).generateToken(anyString(), anyString(), anyInt());
    }

    @Test
    void loginOnSsoAccountKeepsGenericErrorWhenSsoSwitchedOff() {
        SysUser sso = user(1, 0);
        sso.setAuthSource("SSO");
        when(userMapper.selectOne(any())).thenReturn(sso);
        when(ssoProperties.isEnabled()).thenReturn(false);
        when(passwordEncoder.matches("wrong", "encoded")).thenReturn(false);

        BusinessException error = assertThrows(BusinessException.class,
                () -> authService.login(new LoginRequest("u1001", "wrong")));

        assertEquals("用户名或密码错误", error.getMessage());
    }

    @Test
    void loginDisabledAccountIsForbidden() {
        when(userMapper.selectOne(any())).thenReturn(user(0, 3));
        when(passwordEncoder.matches("admin123", "encoded")).thenReturn(true);

        assertThrows(ForbiddenException.class,
                () -> authService.login(new LoginRequest("admin", "admin123")));
    }

    @Test
    void refreshRejectsStaleTokenVersion() {
        when(jwtUtil.validate("old")).thenReturn(true);
        when(jwtUtil.getUsername("old")).thenReturn("admin");
        when(jwtUtil.getTokenVersion("old")).thenReturn(1);
        when(userMapper.selectOne(any())).thenReturn(user(1, 2));

        assertThrows(UnauthorizedException.class, () -> authService.refresh("old"));
    }

    @Test
    void refreshRejectsDisabledAccount() {
        when(jwtUtil.validate("old")).thenReturn(true);
        when(jwtUtil.getUsername("old")).thenReturn("admin");
        when(jwtUtil.getTokenVersion("old")).thenReturn(2);
        when(userMapper.selectOne(any())).thenReturn(user(0, 2));

        assertThrows(UnauthorizedException.class, () -> authService.refresh("old"));
    }

    @Test
    void refreshIssuesTokenWithCurrentVersionAndRole() {
        when(jwtUtil.validate("current")).thenReturn(true);
        when(jwtUtil.getUsername("current")).thenReturn("admin");
        when(jwtUtil.getTokenVersion("current")).thenReturn(2);
        when(userMapper.selectOne(any())).thenReturn(user(1, 2));
        when(jwtUtil.generateToken("admin", "ADMIN", 2)).thenReturn("token-new");

        LoginResponse resp = authService.refresh("current");

        assertEquals("token-new", resp.token());
    }

    @Test
    void loginReportsMustChangePasswordFlag() {
        SysUser stored = user(1, 2);
        stored.setMustChangePassword(1);
        when(userMapper.selectOne(any())).thenReturn(stored);
        when(passwordEncoder.matches("admin123", "encoded")).thenReturn(true);
        when(jwtUtil.generateToken("admin", "ADMIN", 2)).thenReturn("token-abc");

        LoginResponse resp = authService.login(new LoginRequest("admin", "admin123"));

        assertTrue(resp.mustChangePassword());
    }

    @Test
    void changePasswordBumpsTokenVersionAndInvalidatesOldTokens() {
        SysUser stored = user(1, 2);
        stored.setMustChangePassword(1);
        when(userMapper.selectOne(any())).thenReturn(stored);
        when(passwordEncoder.matches("old-pass", "encoded")).thenReturn(true);
        when(passwordEncoder.encode("new-pass")).thenReturn("encoded-new");

        authService.changePassword("admin", new PasswordChangeRequest("old-pass", "new-pass"));

        ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
        verify(userMapper).updateById(captor.capture());
        assertEquals("encoded-new", captor.getValue().getPassword());
        assertEquals(Integer.valueOf(3), captor.getValue().getTokenVersion());
        assertEquals(Integer.valueOf(0), captor.getValue().getMustChangePassword());
    }

    @Test
    void changePasswordRejectsTooShortNewPassword() {
        when(userMapper.selectOne(any())).thenReturn(user(1, 2));
        when(passwordEncoder.matches("old-pass", "encoded")).thenReturn(true);

        assertThrows(BadRequestException.class,
                () -> authService.changePassword("admin", new PasswordChangeRequest("old-pass", "12345")));
        verify(userMapper, never()).updateById(any(SysUser.class));
    }

    @Test
    void changePasswordRejectsSameAsOldPassword() {
        when(userMapper.selectOne(any())).thenReturn(user(1, 2));
        when(passwordEncoder.matches("old-pass", "encoded")).thenReturn(true);

        assertThrows(BadRequestException.class,
                () -> authService.changePassword("admin", new PasswordChangeRequest("old-pass", "old-pass")));
        verify(userMapper, never()).updateById(any(SysUser.class));
    }

    @Test
    void changePasswordRejectsBlankOldPasswordAsBadRequest() {
        when(userMapper.selectOne(any())).thenReturn(user(1, 2));

        assertThrows(BadRequestException.class,
                () -> authService.changePassword("admin", new PasswordChangeRequest(" ", "new-pass")));
    }

    @Test
    void changePasswordRejectsWrongOldPassword() {
        when(userMapper.selectOne(any())).thenReturn(user(1, 2));
        when(passwordEncoder.matches("wrong", "encoded")).thenReturn(false);

        assertThrows(BusinessException.class,
                () -> authService.changePassword("admin", new PasswordChangeRequest("wrong", "new-pass")));
    }
}
