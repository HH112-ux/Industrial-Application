package com.zhizao.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhizao.common.BadRequestException;
import com.zhizao.common.BusinessException;
import com.zhizao.common.ForbiddenException;
import com.zhizao.common.UnauthorizedException;
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

    private final SysUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    /**
     * 构造认证服务。
     *
     * @param userMapper      用户数据访问
     * @param passwordEncoder 密码编码器
     * @param jwtUtil         令牌工具
     */
    public AuthService(SysUserMapper userMapper, PasswordEncoder passwordEncoder, JwtUtil jwtUtil) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    /**
     * 登录：校验密码与账号启停状态，签发携带当前令牌版本的 JWT。
     *
     * @param req 登录请求
     * @return 登录响应，包含令牌与用户信息
     * @throws BusinessException  用户名或密码错误时抛出
     * @throws ForbiddenException 账号已禁用时抛出，返回 HTTP 403
     */
    public LoginResponse login(LoginRequest req) {
        SysUser user = findByUsername(req.username());
        if (user == null || !passwordEncoder.matches(req.password(), user.getPassword())) {
            throw new BusinessException("用户名或密码错误");
        }
        if (!Integer.valueOf(1).equals(user.getEnabled())) {
            throw new ForbiddenException("账号已禁用");
        }
        int tokenVersion = tokenVersion(user);
        String token = jwtUtil.generateToken(user.getUsername(), user.getRole(), tokenVersion);
        return new LoginResponse(token, user.getUsername(), user.getNickname(), user.getRole());
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
        return new LoginResponse(newToken, user.getUsername(), user.getNickname(), user.getRole());
    }

    /**
     * 自助改密：校验原密码后写入新密码，并递增令牌版本使所有旧令牌立即失效。
     *
     * @param username 当前登录用户名
     * @param req      改密请求
     * @throws BadRequestException 原密码为空时抛出，返回 HTTP 400
     * @throws BusinessException   用户不存在、原密码错误或新密码为空时抛出
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
        if (req.newPassword() == null || req.newPassword().isBlank()) {
            throw new BusinessException("新密码不能为空");
        }
        user.setPassword(passwordEncoder.encode(req.newPassword()));
        user.setTokenVersion(tokenVersion(user) + 1);
        userMapper.updateById(user);
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
}
