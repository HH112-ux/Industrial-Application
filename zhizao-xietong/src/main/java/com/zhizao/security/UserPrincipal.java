package com.zhizao.security;

import java.security.Principal;

/**
 * 登录用户主体：由 JWT 过滤器在每次请求时按数据库最新状态构建，不缓存。
 *
 * @param username      用户名
 * @param role          角色
 * @param workshopScope 车间数据域，管理员为 null
 * @author Caspian
 */
public record UserPrincipal(String username, String role, String workshopScope) implements Principal {

    @Override
    public String getName() {
        return username;
    }

    /**
     * 是否管理员。管理员不受车间数据域限制。
     *
     * @return 管理员返回 true
     */
    public boolean isAdmin() {
        return "ADMIN".equalsIgnoreCase(role);
    }
}
