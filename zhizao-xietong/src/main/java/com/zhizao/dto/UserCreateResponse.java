package com.zhizao.dto;

/**
 * 创建账号结果；{@code initialPassword} 只在本次响应返回一次。
 *
 * @author Caspian
 */
public record UserCreateResponse(UserView user, String initialPassword) {
}