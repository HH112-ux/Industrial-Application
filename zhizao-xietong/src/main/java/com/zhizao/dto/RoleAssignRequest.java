package com.zhizao.dto;

/**
 * 管理员调整用户角色请求。
 *
 * @param role 目标角色，允许 ADMIN/USER/WORKER
 * @author Caspian
 */
public record RoleAssignRequest(String role) {
}
