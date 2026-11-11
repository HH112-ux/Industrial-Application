package com.zhizao.sso;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhizao.common.BusinessException;
import com.zhizao.common.ForbiddenException;
import com.zhizao.config.SsoProperties;
import com.zhizao.entity.SysUser;
import com.zhizao.entity.SysUserIdentity;
import com.zhizao.mapper.SysUserIdentityMapper;
import com.zhizao.mapper.SysUserMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 本地身份解析：已绑定则复用，未绑定则 JIT 建档（设计稿 §2.5）。
 *
 * <p>建档规则：username 取外部唯一标识，与既有账号冲突即拒绝、不自动合并；role=WORKER、
 * workshop_scope=NULL、must_change_password=0、auth_source=SSO，密码写随机不可用哈希；
 * 部门只存快照，永不产生车间授权。</p>
 *
 * @author Caspian
 */
@Service
public class DefaultLocalIdentityResolver implements LocalIdentityResolver {

    public static final String AUTH_SOURCE_SSO = "SSO";
    static final String DEFAULT_ROLE = "WORKER";
    private static final int USERNAME_MAX_LENGTH = 32;
    private static final int NICKNAME_MAX_LENGTH = 32;
    private static final int DEPARTMENT_IDS_MAX_LENGTH = 255;

    private final SysUserMapper userMapper;
    private final SysUserIdentityMapper identityMapper;
    private final PasswordEncoder passwordEncoder;
    private final SsoProperties properties;

    public DefaultLocalIdentityResolver(SysUserMapper userMapper, SysUserIdentityMapper identityMapper,
                                        PasswordEncoder passwordEncoder, SsoProperties properties) {
        this.userMapper = userMapper;
        this.identityMapper = identityMapper;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    @Override
    @Transactional
    public SysUser resolve(IdentityUser identity) {
        if (identity == null || !StringUtils.hasText(identity.provider())
                || !StringUtils.hasText(identity.externalId())) {
            throw new BusinessException(400, "外部身份缺少提供方或唯一标识");
        }
        SysUserIdentity binding = identityMapper.selectOne(new LambdaQueryWrapper<SysUserIdentity>()
                .eq(SysUserIdentity::getProvider, identity.provider())
                .eq(SysUserIdentity::getExternalId, identity.externalId()));
        if (binding != null) {
            return reuse(binding, identity);
        }
        if (!properties.isJitEnabled()) {
            throw new BusinessException(403, "账号未开通，请联系管理员");
        }
        if (!identity.active()) {
            throw new ForbiddenException("企业账号已停用，无法登录");
        }
        return create(identity);
    }

    /** 复用账号只刷新展示快照与登录时间，不触碰角色、车间与密码。 */
    private SysUser reuse(SysUserIdentity binding, IdentityUser identity) {
        SysUser user = userMapper.selectById(binding.getUserId());
        if (user == null) {
            throw new BusinessException(409, "身份绑定关系已失效，请联系管理员");
        }
        if (!Integer.valueOf(1).equals(user.getEnabled())) {
            throw new ForbiddenException("账号已被禁用");
        }
        fill(binding, identity);
        identityMapper.updateById(binding);
        return user;
    }

    /** 首次登录建档；用户名冲突时拒绝，避免把 admin 等既有账号自动合并给外部身份。 */
    private SysUser create(IdentityUser identity) {
        String username = identity.externalId();
        if (username.length() > USERNAME_MAX_LENGTH) {
            throw new BusinessException(409, "外部账号标识过长，请联系管理员处理");
        }
        Long existing = userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username));
        if (existing != null && existing > 0) {
            throw new BusinessException(409, "账号未绑定，请联系管理员");
        }
        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
        user.setNickname(StringUtils.hasText(identity.displayName())
                ? truncate(identity.displayName(), NICKNAME_MAX_LENGTH) : username);
        user.setRole(DEFAULT_ROLE);
        user.setEnabled(1);
        user.setTokenVersion(0);
        user.setMustChangePassword(0);
        user.setAuthSource(AUTH_SOURCE_SSO);
        userMapper.insert(user);

        SysUserIdentity binding = new SysUserIdentity();
        binding.setUserId(user.getId());
        binding.setProvider(identity.provider());
        binding.setExternalId(identity.externalId());
        fill(binding, identity);
        identityMapper.insert(binding);
        return user;
    }

    private void fill(SysUserIdentity binding, IdentityUser identity) {
        binding.setExternalUsername(identity.externalUsername());
        binding.setDisplayName(identity.displayName());
        binding.setMobile(identity.mobile());
        binding.setEmail(identity.email());
        binding.setDepartmentIds(departments(identity.departmentIds()));
        binding.setLastLoginAt(LocalDateTime.now());
    }

    private String departments(List<String> departmentIds) {
        if (departmentIds == null || departmentIds.isEmpty()) {
            return null;
        }
        return truncate(String.join(",", departmentIds), DEPARTMENT_IDS_MAX_LENGTH);
    }

    private String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
