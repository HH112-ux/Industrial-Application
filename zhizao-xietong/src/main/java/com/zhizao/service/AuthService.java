package com.zhizao.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhizao.common.BadRequestException;
import com.zhizao.common.BusinessException;
import com.zhizao.common.ForbiddenException;
import com.zhizao.common.PasswordPolicy;
import com.zhizao.common.UnauthorizedException;
import com.zhizao.config.SsoProperties;
import com.zhizao.dto.LoginRequest;
import com.zhizao.dto.LoginResponse;
import com.zhizao.dto.PasswordChangeRequest;
import com.zhizao.entity.SysUser;
import com.zhizao.mapper.SysUserMapper;
import com.zhizao.security.JwtUtil;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 认证服务：登录、令牌续期与自助改密。
 *
 * <p>登录与续期都会读取数据库中的启停状态和令牌版本。禁用账号、角色调整或改密后，
 * 旧令牌在下一次请求即被认证过滤器拒绝，不依赖缓存过期时间。</p>
 *
 * @author Caspian
 */
@Service
public class AuthService {

    /** SSO 建号的账号来源标记；这类账号的本地密码是随机不可用哈希。 */
    private static final String AUTH_SOURCE_SSO = "SSO";

    /** SSO 账号误用本地密码登录时的提示，便于用户改走扫码入口。 */
    static final String SSO_LOGIN_HINT = "该账号需使用企业微信扫码登录";

    private final SysUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final SsoProperties ssoProperties;

    /**
     * 构造认证服务。
     *
     * @param userMapper      用户数据访问
     * @param passwordEncoder 密码编码器
     * @param jwtUtil         令牌工具
     * @param ssoProperties   SSO 灰度开关，关闭时不返回扫码提示
     */
    public AuthService(SysUserMapper userMapper, PasswordEncoder passwordEncoder, JwtUtil jwtUtil,
                       SsoProperties ssoProperties) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.ssoProperties = ssoProperties;
    }

    /**
     * 登录：校验密码与账号启停状态，签发携带当前令牌版本的 JWT。
     *
     * <p>SSO 建号的账号没有可用本地密码，误用密码登录时返回「需扫码登录」而不是
     * 「用户名或密码错误」，否则用户无法判断该走哪个入口。该提示确认了用户名存在，
     * 取舍见 {@link #requiresSsoLogin(SysUser)}。</p>
     *
     * @param req 登录请求
     * @return 登录响应，包含令牌与用户信息
     * @throws BusinessException  用户名或密码错误、或 SSO 账号误用本地密码时抛出
     * @throws ForbiddenException 账号已禁用时抛出，返回 HTTP 403
     */
    public LoginResponse login(LoginRequest req) {
        SysUser user = findByUsername(req.username());
        if (requiresSsoLogin(user)) {
            throw new BusinessException(SSO_LOGIN_HINT);
        }
        if (user == null || !passwordEncoder.matches(req.password(), user.getPassword())) {
            throw new BusinessException("用户名或密码错误");
        }
        if (!Integer.valueOf(1).equals(user.getEnabled())) {
            throw new ForbiddenException("账号已禁用");
        }
        int tokenVersion = tokenVersion(user);
        String token = jwtUtil.generateToken(user.getUsername(), user.getRole(), tokenVersion);
        return new LoginResponse(token, user.getUsername(), user.getNickname(), user.getRole(),
                mustChangePassword(user));
    }

    /**
     * 刷新令牌：再次校验账号启停与令牌版本，用数据库中的最新角色和版本签发新令牌。
     *
     * @param token 当前令牌
     * @return 新的登录响应
     * @throws UnauthorizedException 令牌无效、过期、账号已禁用或版本落后时抛出，返回 HTTP 401
     */
    public LoginResponse refresh(String token) {
        if (token == null || token.isBlank() || !jwtUtil.validate(token)) {
            throw new UnauthorizedException("令牌无效或已过期");
        }
        String username = jwtUtil.getUsername(token);
        Integer tokenVersion = jwtUtil.getTokenVersion(token);
        SysUser user = findByUsername(username);
        if (user == null || !Integer.valueOf(1).equals(user.getEnabled())) {
            throw new UnauthorizedException("账号已禁用或不存在，请重新登录");
        }
        int currentVersion = tokenVersion(user);
        if (tokenVersion == null || tokenVersion != currentVersion) {
            throw new UnauthorizedException("令牌已失效，请重新登录");
        }
        String newToken = jwtUtil.generateToken(user.getUsername(), user.getRole(), currentVersion);
        return new LoginResponse(newToken, user.getUsername(), user.getNickname(), user.getRole(),
                mustChangePassword(user));
    }

    /**
     * 自助改密：校验原密码与新口令强度后写入新密码，递增令牌版本使旧令牌立即失效，
     * 并清除「必须先改密」标记。
     *
     * <p>新口令强度统一交给 {@link PasswordPolicy}，与管理员重置共用同一份规则，
     * 避免管理端与用户端出现两套标准。</p>
     *
     * @param username 当前登录用户名
     * @param req      改密请求
     * @throws BadRequestException 原密码为空、新口令不满足策略或与原口令相同时抛出，返回 HTTP 400
     * @throws BusinessException   用户不存在、原密码错误时抛出
     */
    @Transactional
    public void changePassword(String username, PasswordChangeRequest req) {
        SysUser user = findByUsername(username);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        if (!StringUtils.hasText(req.oldPassword())) {
            throw new BadRequestException("原密码不能为空");
        }
        if (!passwordEncoder.matches(req.oldPassword(), user.getPassword())) {
            throw new BusinessException("原密码错误");
        }
        PasswordPolicy.validate(req.newPassword(), user.getUsername());
        if (passwordEncoder.matches(req.newPassword(), user.getPassword())) {
            throw new BadRequestException("新密码不能与原密码相同");
        }
        user.setPassword(passwordEncoder.encode(req.newPassword()));
        user.setTokenVersion(tokenVersion(user) + 1);
        user.setMustChangePassword(0);
        userMapper.updateById(user);
    }

    /**
     * 判断是否应提示改用扫码登录：仅 SSO 账号且开关开启时成立。
     *
     * <p>可枚举性取舍：提示在密码校验之前返回，因此它确认了「该用户名已开通 SSO 账号」。
     * 接受这一泄露，理由有三：SSO 账号的本地密码是随机哈希，不存在「先验密码再提示」的可行顺序；
     * 提示与提交的密码无关，不能用于校验口令，也不会泄露角色、车间或启停状态；
     * 限流仍在 Controller 按用户名计数，单个账号的爆破成本不变。开关关闭时不提示，
     * 保证 `app.sso.enabled=false` 的对外行为与接入前完全一致。</p>
     *
     * @param user 已查到的用户，可为 null
     * @return true 表示应返回扫码登录提示
     */
    private boolean requiresSsoLogin(SysUser user) {
        return user != null && AUTH_SOURCE_SSO.equals(user.getAuthSource()) && ssoProperties.isEnabled();
    }

    /**
     * 按用户名查询用户。
     *
     * @param username 用户名
     * @return 用户；不存在返回 null
     */
    private SysUser findByUsername(String username) {
        if (!StringUtils.hasText(username)) {
            return null;
        }
        return userMapper.selectOne(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username));
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

    /**
     * 读取强制改密标记，空值按 false 处理。
     *
     * @param user 用户
     * @return true 表示需要先修改密码
     */
    private boolean mustChangePassword(SysUser user) {
        return Integer.valueOf(1).equals(user.getMustChangePassword());
    }
}
