package com.zhizao.config;

import com.zhizao.entity.SysUser;
import com.zhizao.mapper.SysUserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 账号初始化的安全约束测试：禁止硬编码弱口令、演示账号只在 dev 创建。
 *
 * @author Caspian
 */
@ExtendWith(MockitoExtension.class)
class DataInitializerTest {

    @Mock
    private SysUserMapper userMapper;
    @Mock
    private Environment environment;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Test
    void missingPasswordInDevFallsBackToRandomInsteadOfWeakDefault() {
        when(userMapper.selectCount(any())).thenReturn(0L);
        when(environment.acceptsProfiles(any(Profiles.class))).thenReturn(true);
        DataInitializer initializer = new DataInitializer(userMapper, passwordEncoder, environment);

        initializer.run();

        ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
        verify(userMapper, times(2)).insert(captor.capture());
        SysUser admin = captor.getAllValues().get(0);
        assertEquals("admin", admin.getUsername());
        assertEquals("ADMIN", admin.getRole());
        assertNotEquals("admin123", admin.getPassword());
        assertFalse(passwordEncoder.matches("admin123", admin.getPassword()));
        assertTrue(admin.getPassword().startsWith("$2"), "口令必须经过 BCrypt 编码");
    }

    @Test
    void missingPasswordOutsideDevFailsStartupWithoutCreatingAdmin() {
        when(userMapper.selectCount(any())).thenReturn(0L);
        when(environment.acceptsProfiles(any(Profiles.class))).thenReturn(false);
        DataInitializer initializer = new DataInitializer(userMapper, passwordEncoder, environment);

        IllegalStateException error = assertThrows(IllegalStateException.class, initializer::run);

        assertTrue(error.getMessage().contains("APP_BOOTSTRAP_ADMIN_PASSWORD"));
        verify(userMapper, never()).insert(any(SysUser.class));
    }

    @Test
    void productionStillStartsWhenAdminAlreadyExistsWithoutPassword() {
        when(userMapper.selectCount(any())).thenReturn(1L);
        when(environment.acceptsProfiles(any(Profiles.class))).thenReturn(false);
        DataInitializer initializer = new DataInitializer(userMapper, passwordEncoder, environment);

        initializer.run();

        verify(userMapper, never()).insert(any(SysUser.class));
    }

    @Test
    void demoAccountIsCreatedOnlyInDevProfileWithConfiguredPassword() {
        when(userMapper.selectCount(any())).thenReturn(0L);
        when(environment.acceptsProfiles(any(Profiles.class))).thenReturn(true);
        DataInitializer initializer = new DataInitializer(userMapper, passwordEncoder, environment);
        ReflectionTestUtils.setField(initializer, "adminPassword", "admin-strong-pass");
        ReflectionTestUtils.setField(initializer, "demoPassword", "demo-strong-pass");

        initializer.run();

        ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
        verify(userMapper, times(2)).insert(captor.capture());
        List<SysUser> users = captor.getAllValues();
        assertEquals(List.of("admin", "zhangsan"), users.stream().map(SysUser::getUsername).toList());
        assertTrue(passwordEncoder.matches("admin-strong-pass", users.get(0).getPassword()));
        assertTrue(passwordEncoder.matches("demo-strong-pass", users.get(1).getPassword()));
    }

    @Test
    void existingAccountsAreNotRecreated() {
        when(userMapper.selectCount(any())).thenReturn(1L);
        when(environment.acceptsProfiles(any(Profiles.class))).thenReturn(true);
        DataInitializer initializer = new DataInitializer(userMapper, passwordEncoder, environment);

        initializer.run();

        verify(userMapper, never()).insert(any(SysUser.class));
    }
}
