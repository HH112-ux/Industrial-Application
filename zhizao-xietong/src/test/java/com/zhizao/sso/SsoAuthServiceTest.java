package com.zhizao.sso;

import com.zhizao.common.BadRequestException;
import com.zhizao.common.BusinessException;
import com.zhizao.common.ForbiddenException;
import com.zhizao.common.LoginRateLimiter;
import com.zhizao.config.SsoProperties;
import com.zhizao.dto.LoginResponse;
import com.zhizao.entity.SysUser;
import com.zhizao.entity.SysUserIdentity;
import com.zhizao.mapper.SysUserIdentityMapper;
import com.zhizao.mapper.SysUserMapper;
import com.zhizao.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 企微登录编排：假实现驱动首次建号、二次复用、禁用拒绝、开关拒绝与 state 校验。
 *
 * @author Caspian
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SsoAuthServiceTest {

    private static final String BROWSER = "browser-1";
    private static final long FIVE_MINUTES = 5 * 60_000L;

    @Mock
    private SysUserMapper userMapper;
    @Mock
    private SysUserIdentityMapper identityMapper;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private LoginRateLimiter loginRateLimiter;

    private final AtomicLong now = new AtomicLong(1_000_000L);
    private SsoProperties properties;
    private SsoStateStore stateStore;
    private SsoAuthService service;

    @BeforeEach
    void setUp() {
        properties = new SsoProperties();
        properties.setEnabled(true);
        properties.getWecom().setRedirectUri("https://mes.example.com/sso/callback");
        when(passwordEncoder.encode(anyString())).thenReturn("HASH");
        when(userMapper.selectCount(any())).thenReturn(0L);
        when(userMapper.insert(any(SysUser.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, SysUser.class).setId(5L);
            return 1;
        });
        when(jwtUtil.generateToken(anyString(), anyString(), anyInt())).thenReturn("jwt-token");
        stateStore = new SsoStateStore(now::get);
        service = new SsoAuthService(properties, new FakeIdentityProvider(),
                new DefaultLocalIdentityResolver(userMapper, identityMapper, passwordEncoder),
                jwtUtil, loginRateLimiter, stateStore);
    }

    @Test
    void firstLoginCreatesWorkerAccountAndIssuesLocalJwt() {
        LoginResponse response = loginWithFreshState("fake-u1001", "10.0.0.1");

        assertEquals("jwt-token", response.token());
        assertEquals("u1001", response.username());
        assertEquals("WORKER", response.role());
        assertFalse(response.mustChangePassword());
        verify(loginRateLimiter).check("sso:10.0.0.1");
        verify(jwtUtil).generateToken("u1001", "WORKER", 0);
        verify(identityMapper).insert(any(SysUserIdentity.class));
    }

    @Test
    void secondLoginReusesExistingAccount() {
        SysUser existing = new SysUser();
        existing.setId(9L);
        existing.setUsername("u1001");
        existing.setRole("WORKER");
        existing.setEnabled(1);
        existing.setTokenVersion(3);
        SysUserIdentity binding = new SysUserIdentity();
        binding.setUserId(9L);
        when(identityMapper.selectOne(any())).thenReturn(binding);
        when(userMapper.selectById(9L)).thenReturn(existing);

        LoginResponse response = loginWithFreshState("fake-u1001", "10.0.0.2");

        assertEquals("jwt-token", response.token());
        verify(userMapper, never()).insert(any(SysUser.class));
        verify(jwtUtil).generateToken("u1001", "WORKER", 3);
    }

    @Test
    void rejectsDisabledAccount() {
        SysUser disabled = new SysUser();
        disabled.setId(9L);
        disabled.setEnabled(0);
        SysUserIdentity binding = new SysUserIdentity();
        binding.setUserId(9L);
        when(identityMapper.selectOne(any())).thenReturn(binding);
        when(userMapper.selectById(9L)).thenReturn(disabled);
        String state = stateStore.issue(BROWSER);

        assertThrows(ForbiddenException.class,
                () -> service.login("fake-u1001", state, BROWSER, "10.0.0.3"));
        verify(jwtUtil, never()).generateToken(anyString(), anyString(), anyInt());
    }

    @Test
    void rejectsWhenSsoSwitchOff() {
        properties.setEnabled(false);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.login("fake-u1001", stateStore.issue(BROWSER), BROWSER, "10.0.0.4"));

        assertEquals(503, error.getCode());
        verifyNoInteractions(loginRateLimiter, jwtUtil, userMapper, identityMapper);
    }

    @Test
    void rejectsMalformedAuthorizationCode() {
        assertThrows(BadRequestException.class,
                () -> loginWithFreshState("bad-code", "10.0.0.5"));
        verifyNoInteractions(jwtUtil);
    }

    @Test
    void rejectsForgedState() {
        BadRequestException error = assertThrows(BadRequestException.class,
                () -> service.login("fake-u1001", "forged-state", BROWSER, "10.0.0.6"));

        assertEquals("登录会话已失效，请重新扫码登录", error.getMessage());
        verifyNoInteractions(jwtUtil);
    }

    @Test
    void rejectsExpiredState() {
        String state = stateStore.issue(BROWSER);
        now.addAndGet(FIVE_MINUTES + 1);

        BadRequestException error = assertThrows(BadRequestException.class,
                () -> service.login("fake-u1001", state, BROWSER, "10.0.0.7"));

        assertEquals("登录会话已失效，请重新扫码登录", error.getMessage());
    }

    @Test
    void rejectsReusedStateOnSecondCallback() {
        String state = stateStore.issue(BROWSER);
        service.login("fake-u1001", state, BROWSER, "10.0.0.8");

        BadRequestException error = assertThrows(BadRequestException.class,
                () -> service.login("fake-u1001", state, BROWSER, "10.0.0.8"));

        assertEquals("登录会话已失效，请重新扫码登录", error.getMessage());
        verify(userMapper, times(1)).insert(any(SysUser.class));
    }

    @Test
    void rejectsStateIssuedToAnotherBrowser() {
        String state = stateStore.issue("another-browser");

        BadRequestException error = assertThrows(BadRequestException.class,
                () -> service.login("fake-u1001", state, BROWSER, "10.0.0.9"));

        assertEquals("登录会话已失效，请重新扫码登录", error.getMessage());
        verifyNoInteractions(jwtUtil);
    }

    @Test
    void rejectsMissingStateOrBrowserCookie() {
        assertThrows(BadRequestException.class,
                () -> service.login("fake-u1001", null, BROWSER, "10.0.0.10"));
        assertThrows(BadRequestException.class,
                () -> service.login("fake-u1001", stateStore.issue(BROWSER), null, "10.0.0.10"));
        verifyNoInteractions(jwtUtil);
    }

    @Test
    void statusReportsSwitchStateForLoginPage() {
        assertTrue(service.status().enabled());

        properties.setEnabled(false);

        assertFalse(service.status().enabled());
    }

    private LoginResponse loginWithFreshState(String code, String ip) {
        String state = stateStore.issue(BROWSER);
        return service.login(code, state, BROWSER, ip);
    }
}
