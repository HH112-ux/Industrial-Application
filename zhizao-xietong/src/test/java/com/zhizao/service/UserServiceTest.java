package com.zhizao.service;

import com.zhizao.common.BadRequestException;
import com.zhizao.common.BusinessException;
import com.zhizao.entity.SysUser;
import com.zhizao.mapper.SysUserIdentityMapper;
import com.zhizao.mapper.SysUserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private SysUserMapper userMapper;

    @Mock
    private SysUserIdentityMapper identityMapper;

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
    void resettingPasswordBumpsTokenVersionAndForcesChange() {
        when(userMapper.selectById(1L)).thenReturn(user());
        when(passwordEncoder.encode("new-pass")).thenReturn("encoded-new");

        userService.resetPassword(1L, "new-pass");

        ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
        verify(userMapper).updateById(captor.capture());
        assertEquals("encoded-new", captor.getValue().getPassword());
        assertEquals(Integer.valueOf(3), captor.getValue().getTokenVersion());
        assertEquals(Integer.valueOf(1), captor.getValue().getMustChangePassword());
    }

    @Test
    void resettingPasswordRejectsPasswordContainingUsername() {
        when(userMapper.selectById(1L)).thenReturn(user());

        assertThrows(BadRequestException.class, () -> userService.resetPassword(1L, "zhangsan2026"));
        verify(userMapper, never()).updateById(org.mockito.ArgumentMatchers.<SysUser>any());
    }

    @Test
    void assigningWorkshopTrimsValueAndKeepsTokenVersion() {
        when(userMapper.selectById(1L)).thenReturn(user());

        userService.assignWorkshop(1L, "  二车间  ");

        ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
        verify(userMapper).updateById(captor.capture());
        assertEquals("二车间", captor.getValue().getWorkshopScope());
        assertEquals(Integer.valueOf(2), captor.getValue().getTokenVersion());
    }

    @Test
    void assigningBlankWorkshopClearsScope() {
        when(userMapper.selectById(1L)).thenReturn(user());

        userService.assignWorkshop(1L, "  ");

        ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
        verify(userMapper).updateById(captor.capture());
        assertNull(captor.getValue().getWorkshopScope());
    }

    @Test
    void assigningWorkshopToAdminIsRejected() {
        SysUser admin = user();
        admin.setRole("ADMIN");
        when(userMapper.selectById(1L)).thenReturn(admin);

        assertThrows(BusinessException.class, () -> userService.assignWorkshop(1L, "一车间"));
        verify(userMapper, never()).updateById(org.mockito.ArgumentMatchers.<SysUser>any());
    }

    @Test
    void assigningRoleNormalizesCaseAndBumpsTokenVersion() {
        when(userMapper.selectById(1L)).thenReturn(user());

        userService.assignRole(1L, " worker ");

        ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
        verify(userMapper).updateById(captor.capture());
        assertEquals("WORKER", captor.getValue().getRole());
        assertEquals(Integer.valueOf(3), captor.getValue().getTokenVersion());
    }

    @Test
    void assigningUnknownRoleIsRejected() {
        when(userMapper.selectById(1L)).thenReturn(user());

        BusinessException error = assertThrows(BusinessException.class, () -> userService.assignRole(1L, "ROOT"));

        assertEquals(400, error.getCode());
        verify(userMapper, never()).updateById(org.mockito.ArgumentMatchers.<SysUser>any());
    }

    @Test
    void assigningSameRoleDoesNotInvalidateCurrentToken() {
        when(userMapper.selectById(1L)).thenReturn(user());

        userService.assignRole(1L, "user");

        verify(userMapper, never()).updateById(org.mockito.ArgumentMatchers.<SysUser>any());
    }

    @Test
    void demotingLastLocalAdminIsRejected() {
        SysUser admin = user();
        admin.setRole("ADMIN");
        admin.setAuthSource("LOCAL");
        when(userMapper.selectById(1L)).thenReturn(admin);
        when(userMapper.selectCount(org.mockito.ArgumentMatchers.any())).thenReturn(1L);

        assertThrows(BusinessException.class, () -> userService.assignRole(1L, "USER"));
        verify(userMapper, never()).updateById(org.mockito.ArgumentMatchers.<SysUser>any());
    }

    @Test
    void demotingLocalAdminIsAllowedWhenAnotherLocalAdminExists() {
        SysUser admin = user();
        admin.setRole("ADMIN");
        admin.setAuthSource("LOCAL");
        when(userMapper.selectById(1L)).thenReturn(admin);
        when(userMapper.selectCount(org.mockito.ArgumentMatchers.any())).thenReturn(2L);

        userService.assignRole(1L, "USER");

        ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
        verify(userMapper).updateById(captor.capture());
        assertEquals("USER", captor.getValue().getRole());
    }
}
