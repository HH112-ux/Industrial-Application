package com.zhizao.service;

import com.zhizao.common.BusinessException;
import com.zhizao.dto.LoginRequest;
import com.zhizao.dto.LoginResponse;
import com.zhizao.entity.SysUser;
import com.zhizao.mapper.SysUserMapper;
import com.zhizao.security.JwtUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private SysUserMapper userMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUtil jwtUtil;

    @InjectMocks
    private AuthService authService;

    @Test
    void loginSuccess() {
        SysUser user = new SysUser();
        user.setUsername("admin");
        user.setPassword("encoded");
        user.setNickname("系统管理员");
        user.setRole("ADMIN");
        when(userMapper.selectOne(any())).thenReturn(user);
        when(passwordEncoder.matches("admin123", "encoded")).thenReturn(true);
        when(jwtUtil.generateToken("admin", "ADMIN")).thenReturn("token-abc");

        LoginResponse resp = authService.login(new LoginRequest("admin", "admin123"));

        assertEquals("token-abc", resp.token());
        assertEquals("admin", resp.username());
        assertEquals("系统管理员", resp.nickname());
        assertEquals("ADMIN", resp.role());
    }

    @Test
    void loginWrongPasswordThrows() {
        SysUser user = new SysUser();
        user.setUsername("admin");
        user.setPassword("encoded");
        when(userMapper.selectOne(any())).thenReturn(user);
        when(passwordEncoder.matches("wrong", "encoded")).thenReturn(false);

        assertThrows(BusinessException.class,
                () -> authService.login(new LoginRequest("admin", "wrong")));
    }

    @Test
    void loginUnknownUserThrows() {
        when(userMapper.selectOne(any())).thenReturn(null);

        assertThrows(BusinessException.class,
                () -> authService.login(new LoginRequest("nobody", "x")));
    }
}
