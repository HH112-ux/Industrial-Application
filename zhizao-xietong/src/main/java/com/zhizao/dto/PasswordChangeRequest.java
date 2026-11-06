package com.zhizao.dto;

/**
 * 自助改密请求。
 *
 * @param oldPassword 原密码
 * @param newPassword 新密码
 * @author Caspian
 */
public record PasswordChangeRequest(String oldPassword, String newPassword) {
}
