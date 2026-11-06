package com.zhizao.service;

import com.zhizao.entity.SysUser;
import com.zhizao.mapper.SysUserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private SysUserMapper userMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    private SysUser user() {
        SysUser user = new SysUser();
        user.setId(1L);
        user.setUsername("zhangsan");
        user.setPassword("encoded");
        user.setRole("USER");
        user.setEnabled(1);
        user.setTokenVersion(2);
        return user;
    }

    @Test
    void disablingUserBumpsTokenVersionSoOldTokensDie() {
        when(userMapper.selectById(1L)).thenReturn(user());

        userService.setEnabled(1L, false);

        ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
        verify(userMapper).updateById(captor.capture());
        assertEquals(Integer.valueOf(0), captor.getValue().getEnabled());
        assertEquals(Integer.valueOf(3), captor.getValue().getTokenVersion());
    }

    @Test
    void enablingUserAlsoBumpsTokenVersionToAvoidRevival() {
        SysUser disabled = user();
        disabled.setEnabled(0);
        when(userMapper.selectById(1L)).thenReturn(disabled);

        userService.setEnabled(1L, true);

        ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
        verify(userMapper).updateById(captor.capture());
        assertEquals(Integer.valueOf(1), captor.getValue().getEnabled());
        assertEquals(Integer.valueOf(3), captor.getValue().getTokenVersion());
    }

    @Test
    void resettingPasswordBumpsTokenVersion() {
        when(userMapper.selectById(1L)).thenReturn(user());
        when(passwordEncoder.encode("new-pass")).thenReturn("encoded-new");

        userService.resetPassword(1L, "new-pass");

        ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
        verify(userMapper).updateById(captor.capture());
        assertEquals("encoded-new", captor.getValue().getPassword());
        assertEquals(Integer.valueOf(3), captor.getValue().getTokenVersion());
    }
}
