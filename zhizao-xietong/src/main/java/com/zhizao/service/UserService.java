package com.zhizao.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhizao.common.BadRequestException;
import com.zhizao.common.BusinessException;
import com.zhizao.common.PageResult;
import com.zhizao.common.PasswordPolicy;
import com.zhizao.dto.UserView;
import com.zhizao.entity.SysUser;
import com.zhizao.entity.SysUserIdentity;
import com.zhizao.mapper.SysUserIdentityMapper;
import com.zhizao.mapper.SysUserMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 用户管理服务：列表、启停、角色与车间分配、管理员重置密码。
 *
 * <p>启停、重置密码与角色调整都会递增令牌版本。认证过滤器每次请求比对版本并重新读取角色，
 * 因此操作完成后该用户的旧令牌在下一次请求即失效，不需要等待缓存过期。</p>
 *
 * @author Caspian
 */
@Service
public class UserService {

    /** 管理员可分配的角色，WORKER 与 USER 等价，均非管理员。 */
    private static final Set<String> ASSIGNABLE_ROLES = Set.of("ADMIN", "USER", "WORKER");
    private static final String ROLE_ADMIN = "ADMIN";
    private static final String AUTH_SOURCE_LOCAL = "LOCAL";

    private final SysUserMapper userMapper;
    private final SysUserIdentityMapper identityMapper;
    private final PasswordEncoder passwordEncoder;

    /**
     * 构造用户管理服务。
     *
     * @param userMapper      用户数据访问
     * @param identityMapper  外部身份映射，用于展示企微部门建议值
     * @param passwordEncoder 密码编码器
     */
    public UserService(SysUserMapper userMapper, SysUserIdentityMapper identityMapper,
                       PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.identityMapper = identityMapper;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 分页查询用户，返回脱敏视图与企微部门快照。
     *
     * @param pageNum  页码
     * @param pageSize 每页条数
     * @param keyword  用户名或昵称模糊匹配，可为空
     * @return 用户分页结果
     */
    public PageResult<UserView> list(int pageNum, int pageSize, String keyword) {
        Page<SysUser> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<SysUser> qw = new LambdaQueryWrapper<>();
        qw.and(StringUtils.hasText(keyword), w -> w
                .like(SysUser::getUsername, keyword)
                .or().like(SysUser::getNickname, keyword));
        qw.orderByAsc(SysUser::getId);
        userMapper.selectPage(page, qw);
        Map<Long, SysUserIdentity> identities = identities(page.getRecords());
        return PageResult.of(page.getTotal(), page.getRecords().stream()
                .map(user -> UserView.of(user, identities.get(user.getId()))).toList());
    }

    /**
     * 查询当前登录用户自身信息，包含强制改密标记。
     *
     * @param username 用户名
     * @return 用户视图
     * @throws BusinessException 用户不存在时抛出
     */
    public UserView profile(String username) {
        SysUser user = userMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username));
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        return UserView.of(user);
    }

    /**
     * 启用或禁用用户，并递增令牌版本。
     *
     * @param id      用户主键
     * @param enabled true 启用，false 禁用
     * @return 更新后的用户视图
     * @throws BusinessException 用户不存在时抛出
     */
    @Transactional
    public UserView setEnabled(Long id, boolean enabled) {
        SysUser user = requireUser(id);
        user.setEnabled(enabled ? 1 : 0);
        user.setTokenVersion(tokenVersion(user) + 1);
        userMapper.updateById(user);
        return UserView.of(user);
    }

    /**
     * 管理员重置密码：校验强度后写入新密码，递增令牌版本使该用户所有旧令牌立即失效，
     * 并置「必须先改密」，让管理员掌握的临时密码只能使用一次。
     *
     * <p>强度规则与自助改密共用 {@link PasswordPolicy}，管理员不能设置比用户更弱的密码。
     * 「必须先改密」的拦截沿用认证过滤器既有逻辑，不另造一套。</p>
     *
     * @param id          用户主键
     * @param newPassword 新密码
     * @throws BusinessException      用户不存在时抛出
     * @throws BadRequestException    新密码不满足策略时抛出，返回 HTTP 400
     */
    @Transactional
    public void resetPassword(Long id, String newPassword) {
        SysUser user = requireUser(id);
        PasswordPolicy.validate(newPassword, user.getUsername());
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setTokenVersion(tokenVersion(user) + 1);
        user.setMustChangePassword(1);
        userMapper.updateById(user);
    }

    /**
     * 管理员分配或撤销非管理员的车间数据域。
     *
     * <p>认证过滤器每次请求重新查库并构建用户主体，因此分配结果在下一次请求即生效，
     * 不需要递增令牌版本。管理员本身不受车间限制，拒绝分配以免造成误解。</p>
     *
     * <p>车间名称传空表示撤销分配，用户随即看不到任何车间数据。</p>
     *
     * @param id            用户主键
     * @param workshopScope 车间名称；为空时清除数据域
     * @return 更新后的用户视图
     * @throws BusinessException 用户不存在或目标为管理员时抛出
     */
    @Transactional
    public UserView assignWorkshop(Long id, String workshopScope) {
        SysUser user = requireUser(id);
        if (ROLE_ADMIN.equals(user.getRole())) {
            throw new BusinessException("管理员不受车间数据域限制，无需分配车间");
        }
        user.setWorkshopScope(StringUtils.hasText(workshopScope) ? workshopScope.trim() : null);
        userMapper.updateById(user);
        return UserView.of(user);
    }

    /**
     * 管理员调整用户角色。
     *
     * <p>角色变更会对审计留下记录（{@code AuditAspect} 覆盖本控制器的写接口）。
     * 由于过滤器每次请求从库读取角色，授权在下一次请求即按新角色生效；同时递增令牌版本，
     * 让前端缓存的角色随旧令牌一起失效、重新登录。</p>
     *
     * <p>降级最后一个启用的本地管理员会切断企微故障时的应急回退路径，直接拒绝。</p>
     *
     * @param id   用户主键
     * @param role 目标角色，允许 ADMIN/USER/WORKER，大小写不敏感
     * @return 更新后的用户视图
     * @throws BusinessException 用户不存在、角色非法或降级最后一个本地管理员时抛出
     */
    @Transactional
    public UserView assignRole(Long id, String role) {
        SysUser user = requireUser(id);
        String normalized = role == null ? "" : role.trim().toUpperCase(Locale.ROOT);
        if (!ASSIGNABLE_ROLES.contains(normalized)) {
            throw new BusinessException(400, "角色只能是 ADMIN、USER 或 WORKER");
        }
        if (normalized.equals(user.getRole())) {
            return UserView.of(user);
        }
        if (AUTH_SOURCE_LOCAL.equals(user.getAuthSource()) && ROLE_ADMIN.equals(user.getRole())
                && !ROLE_ADMIN.equals(normalized)
                && Integer.valueOf(1).equals(user.getEnabled()) && isLastLocalAdmin()) {
            throw new BusinessException("至少保留一个启用的本地管理员账号作为应急回退");
        }
        user.setRole(normalized);
        user.setTokenVersion(tokenVersion(user) + 1);
        userMapper.updateById(user);
        return UserView.of(user);
    }

    /**
     * 判断当前是否只剩一个启用的本地管理员。
     *
     * @return true 表示再降级将失去本地应急入口
     */
    private boolean isLastLocalAdmin() {
        Long count = userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getRole, ROLE_ADMIN)
                .eq(SysUser::getEnabled, 1)
                .eq(SysUser::getAuthSource, AUTH_SOURCE_LOCAL));
        return count == null || count <= 1;
    }

    /**
     * 批量查询当页用户的外部身份绑定。
     *
     * @param users 当页用户
     * @return 以用户主键为键的身份绑定
     */
    private Map<Long, SysUserIdentity> identities(List<SysUser> users) {
        if (users.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = users.stream().map(SysUser::getId).toList();
        return identityMapper.selectList(new LambdaQueryWrapper<SysUserIdentity>()
                        .in(SysUserIdentity::getUserId, ids)).stream()
                .collect(Collectors.toMap(SysUserIdentity::getUserId, Function.identity(), (a, b) -> a));
    }

    /**
     * 查询用户，不存在时按业务拒绝处理。
     *
     * @param id 用户主键
     * @return 用户实体
     * @throws BusinessException 用户不存在时抛出
     */
    private SysUser requireUser(Long id) {
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        return user;
    }

    /**
     * 读取令牌版本，空值按 0 处理。
     *
     * @param user 用户
     * @return 当前令牌版本
     */
    private int tokenVersion(SysUser user) {
        return user.getTokenVersion() == null ? 0 : user.getTokenVersion();
    }
}
