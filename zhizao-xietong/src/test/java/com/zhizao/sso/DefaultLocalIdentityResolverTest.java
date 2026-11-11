package com.zhizao.sso;

import com.zhizao.common.BusinessException;
import com.zhizao.common.ForbiddenException;
import com.zhizao.entity.SysUser;
import com.zhizao.entity.SysUserIdentity;
import com.zhizao.mapper.SysUserIdentityMapper;
import com.zhizao.mapper.SysUserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** JIT 建档与身份复用：首次建号取最低权限、二次登录复用、冲突与禁用拒绝。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DefaultLocalIdentityResolverTest {

    private static final String HASH = "$2a$10$randomized-unusable-hash";

    @Mock
    private SysUserMapper userMapper;
    @Mock
    private SysUserIdentityMapper identityMapper;
    @Mock
    private PasswordEncoder passwordEncoder;

    private DefaultLocalIdentityResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new DefaultLocalIdentityResolver(userMapper, identityMapper, passwordEncoder);
        when(passwordEncoder.encode(anyString())).thenReturn(HASH);
        when(userMapper.selectCount(any())).thenReturn(0L);
        when(userMapper.insert(any(SysUser.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, SysUser.class).setId(77L);
            return 1;
        });
    }

    @Test
    void firstLoginCreatesWorkerWithoutWorkshopScope() {
        SysUser user = resolver.resolve(identity("u1001", "张三", true));

        assertEquals("u1001", user.getUsername());
        assertEquals("WORKER", user.getRole());
        assertEquals("SSO", user.getAuthSource());
        assertEquals(Integer.valueOf(0), user.getMustChangePassword());
        assertNull(user.getWorkshopScope(), "SSO 用户不得自动获得车间数据域");
        assertEquals(HASH, user.getPassword(), "密码必须是随机不可用哈希");

        ArgumentCaptor<SysUserIdentity> captor = ArgumentCaptor.forClass(SysUserIdentity.class);
        verify(identityMapper).insert(captor.capture());
        assertEquals("1,2", captor.getValue().getDepartmentIds());
    }

    @Test
    void secondLoginReusesSameAccountAndRefreshesSnapshot() {
        SysUser existing = user(9L, 1);
        SysUserIdentity binding = new SysUserIdentity();
        binding.setUserId(9L);
        when(identityMapper.selectOne(any())).thenReturn(binding);
        when(userMapper.selectById(9L)).thenReturn(existing);

        assertSame(existing, resolver.resolve(identity("u1001", "张三（新）", true)));
        verify(userMapper, never()).insert(any(SysUser.class));
        verify(identityMapper).updateById(binding);
        assertEquals("张三（新）", binding.getDisplayName());
    }

    @Test
    void rejectsUsernameConflictInsteadOfMerging() {
        when(userMapper.selectCount(any())).thenReturn(1L);

        BusinessException error = assertThrows(BusinessException.class,
                () -> resolver.resolve(identity("admin", "管理员", true)));

        assertEquals(409, error.getCode());
        verify(identityMapper, never()).insert(any(SysUserIdentity.class));
    }

    @Test
    void rejectsDisabledLocalAccount() {
        SysUserIdentity binding = new SysUserIdentity();
        binding.setUserId(9L);
        when(identityMapper.selectOne(any())).thenReturn(binding);
        when(userMapper.selectById(9L)).thenReturn(user(9L, 0));

        assertThrows(ForbiddenException.class, () -> resolver.resolve(identity("u1001", "张三", true)));
        verify(identityMapper, never()).updateById(any(SysUserIdentity.class));
    }

    @Test
    void rejectsInactiveMemberOnFirstLogin() {
        assertThrows(ForbiddenException.class, () -> resolver.resolve(identity("u1002", "离职成员", false)));
        verify(userMapper, never()).insert(any(SysUser.class));
    }

    private SysUser user(Long id, int enabled) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setEnabled(enabled);
        return user;
    }

    private IdentityUser identity(String externalId, String displayName, boolean active) {
        return new IdentityUser("FAKE", externalId, externalId, displayName, null, null, List.of("1", "2"), active);
    }
}
