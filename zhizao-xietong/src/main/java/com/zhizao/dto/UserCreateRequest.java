package com.zhizao.dto;

/**
 * 管理员创建本地账号请求；{@code password} 留空时由服务端生成初始密码。
 *
 * @author Caspian
 */
public record UserCreateRequest(String username, String nickname, String role, String workshopScope, String password) {
}
