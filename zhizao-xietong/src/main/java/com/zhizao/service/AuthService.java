package com.zhizao.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhizao.common.BusinessException;
import com.zhizao.dto.LoginRequest;
import com.zhizao.dto.LoginResponse;
import com.zhizao.entity.SysUser;
import com.zhizao.mapper.SysUserMapper;
import com.zhizao.security.JwtUtil;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final SysUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthService(SysUserMapper userMapper, PasswordEncoder passwordEncoder, JwtUtil jwtUtil) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    public LoginResponse login(LoginRequest req) {
        SysUser user = userMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, req.username()));
        if (user == null || !passwordEncoder.matches(req.password(), user.getPassword())) {
            throw new BusinessException("用户名或密码错误");
        }
        String token = jwtUtil.generateToken(user.getUsername(), user.getRole());
        return new LoginResponse(token, user.getUsername(), user.getNickname(), user.getRole());
    }

    public LoginResponse refresh(String token) {
        if (token == null || token.isBlank() || !jwtUtil.validate(token)) {
            throw new BusinessException("令牌无效或已过期");
        }
        String username = jwtUtil.getUsername(token);
        String role = jwtUtil.getRole(token);
        String newToken = jwtUtil.generateToken(username, role);
        SysUser user = userMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username));
        String nickname = user != null ? user.getNickname() : username;
        return new LoginResponse(newToken, username, nickname, role);
    }
}
