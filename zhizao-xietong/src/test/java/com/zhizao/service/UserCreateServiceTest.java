package com.zhizao.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.zhizao.common.BadRequestException;
import com.zhizao.common.BusinessException;
import com.zhizao.config.SsoProperties;
import com.zhizao.dto.LoginRequest;
import com.zhizao.dto.LoginResponse;
import com.zhizao.dto.UserCreateRequest;
import com.zhizao.dto.UserCreateResponse;
import com.zhizao.entity.SysUser;
import com.zhizao.mapper.SysUserIdentityMapper;
import com.zhizao.mapper.SysUserMapper;
import com.zhizao.security.JwtAuthenticationFilter;
import com.zhizao.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 创建账号：初始密码可登录、首登强制改密、唯一键冲突与参数校验。 */
@ExtendWith(MockitoExtension.class)
class UserCreateServiceTest {
    @Mock
    private SysUserMapper userMapper;
    @Mock
    private SysUserIdentityMapper identityMapper;
    @Mock
    private JwtUtil jwtUtil;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private UserService userService;
    private AuthService authService;
    private SysUser inserted;

    @BeforeEach
    void setUp() {
        userService = new UserService(userMapper, identityMapper, passwordEncoder);
        authService = new AuthService(userMapper, passwordEncoder, jwtUtil, new SsoProperties());
        lenient().when(userMapper.insert(any(SysUser.class))).thenAnswer(invocation -> {
            inserted = invocation.getArgument(0);
            inserted.setId(7L);
            return 1;
        });
    }

    @Test
    void createdUserCanLogInButIsBlockedUntilPasswordChanged() throws Exception {
        UserCreateResponse response = userService.create(
                new UserCreateRequest("wangwu", "王五", "WORKER", "一车间", null));

        assertEquals(12, response.initialPassword().length());
        assertEquals(1, inserted.getMustChangePassword());
        assertEquals("LOCAL", inserted.getAuthSource());
        assertTrue(passwordEncoder.matches(response.initialPassword(), inserted.getPassword()));

        when(userMapper.selectOne(any())).thenReturn(inserted);
        when(jwtUtil.generateToken("wangwu", "WORKER", 0)).thenReturn("jwt");
        LoginResponse login = authService.login(new LoginRequest("wangwu", response.initialPassword()));
        assertTrue(login.mustChangePassword());

        when(jwtUtil.validate("jwt")).thenReturn(true);
        when(jwtUtil.getUsername("jwt")).thenReturn("wangwu");
        when(jwtUtil.getTokenVersion("jwt")).thenReturn(0);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/work-orders");
        request.addHeader("Authorization", "Bearer jwt");
        MockHttpServletResponse filterResponse = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        new JwtAuthenticationFilter(jwtUtil, userMapper).doFilter(request, filterResponse, chain);

        assertEquals(403, filterResponse.getStatus());
        assertTrue(filterResponse.getContentAsString().contains("40301"));

        UserCreateResponse explicit = userService.create(
                new UserCreateRequest("lisi", "李四", "USER", null, "StrongPass9"));
        assertNull(explicit.initialPassword());
        assertTrue(passwordEncoder.matches("StrongPass9", inserted.getPassword()));
    }

    @Test
    void generatedPasswordIsNotWrittenToLogs() {
        Logger logger = (Logger) LoggerFactory.getLogger(UserService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        String plain = userService.create(
                new UserCreateRequest("wangwu", "王五", "USER", "一车间", null)).initialPassword();
        logger.detachAppender(appender);
        assertTrue(appender.list.stream().noneMatch(e -> e.getFormattedMessage().contains(plain)));
    }

    @Test
    void duplicateUsernameIsReportedAs409() {
        when(userMapper.insert(any(SysUser.class))).thenThrow(new DuplicateKeyException("uk_username"));

        BusinessException error = assertThrows(BusinessException.class, () -> userService.create(
                new UserCreateRequest("wangwu", "王五", "USER", null, "StrongPass9")));

        assertEquals(409, error.getCode());
        assertTrue(error.getMessage().contains("用户名已存在"));
    }

    /** 车间为自由字符串：设备表里没有该车间也应允许分配。 */
    @Test
    void workshopWithoutDevicesIsAccepted() {
        userService.create(new UserCreateRequest("wangwu", "王五", "USER", "九车间", null));

        assertEquals("九车间", inserted.getWorkshopScope());
    }

    @Test
    void invalidInputIsRejectedWith400() {
        List<UserCreateRequest> bad = List.of(
                new UserCreateRequest("wangwu", "王五", "ROOT", null, null),
                new UserCreateRequest("wang wu", "王五", "USER", null, null),
                new UserCreateRequest("wangwu", "王五", "USER", null, "short"),
                new UserCreateRequest("wangwu", "王五", "USER", "   ", null),
                new UserCreateRequest("wangwu", "王五", "USER", "车".repeat(33), null));

        for (UserCreateRequest request : bad) {
            assertThrows(BadRequestException.class, () -> userService.create(request));
        }
        verify(userMapper, never()).insert(any(SysUser.class));
    }
}
