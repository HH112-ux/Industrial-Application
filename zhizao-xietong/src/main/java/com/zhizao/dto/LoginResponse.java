package com.zhizao.dto;

/**
 * 登录响应：JWT + 用户信息。
 *
 * @param token              令牌
 * @param username           用户名
 * @param nickname           昵称
 * @param role               角色
 * @param mustChangePassword 是否需要先修改密码才能访问业务接口
 */
public record LoginResponse(String token, String username, String nickname, String role,
                            boolean mustChangePassword) {
}
