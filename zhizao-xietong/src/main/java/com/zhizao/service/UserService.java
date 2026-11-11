package com.zhizao.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhizao.common.BusinessException;
import com.zhizao.common.PageResult;
import com.zhizao.dto.UserView;
import com.zhizao.entity.SysUser;
import com.zhizao.mapper.SysUserMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 用户管理服务：列表、启停与管理员重置密码。
 *
 * <p>启停与重置密码都会递增令牌版本。认证过滤器每次请求比对版本，
 * 因此操作完成后该用户的旧令牌在下一次请求即失效，不需要等待缓存过期。</p>
 *
 * @author Caspian
 */
@Service
public class UserService {

    private final SysUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    /**
     * 构造用户管理服务。
     *
     * @param userMapper      用户数据访问
     * @param passwordEncoder 密码编码器
     */
    public UserService(SysUserMapper userMapper, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 分页查询用户，返回脱敏视图。
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
        return PageResult.of(page.getTotal(), page.getRecords().stream().map(UserView::of).toList());
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
     * 管理员重置密码，并递增令牌版本使该用户所有旧令牌立即失效。
     *
     * @param id          用户主键
     * @param newPassword 新密码
     * @throws BusinessException 用户不存在或新密码为空时抛出
     */
    @Transactional
    public void resetPassword(Long id, String newPassword) {
        SysUser user = requireUser(id);
        if (newPassword == null || newPassword.isBlank()) {
            throw new BusinessException("新密码不能为空");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setTokenVersion(tokenVersion(user) + 1);
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
        if ("ADMIN".equals(user.getRole())) {
            throw new BusinessException("管理员不受车间数据域限制，无需分配车间");
        }
        user.setWorkshopScope(StringUtils.hasText(workshopScope) ? workshopScope.trim() : null);
        userMapper.updateById(user);
        return UserView.of(user);
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
