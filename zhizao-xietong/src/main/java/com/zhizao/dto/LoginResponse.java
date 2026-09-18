package com.zhizao.dto;

/**
 * 登录响应：JWT + 用户信息。
 */
public record LoginResponse(String token, String username, String nickname, String role) {
}
