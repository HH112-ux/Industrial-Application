package com.zhizao.dto;

/**
 * 管理员重置用户密码请求。
 *
 * @param newPassword 新密码
 * @author Caspian
 */
public record PasswordResetRequest(String newPassword) {
}
