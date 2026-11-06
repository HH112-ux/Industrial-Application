package com.zhizao.dto;

import com.zhizao.entity.SysUser;

import java.time.LocalDateTime;

/**
 * 用户列表视图：不包含密码等敏感字段。
 *
 * @param id            用户主键
 * @param username      用户名
 * @param nickname      昵称
 * @param role          角色
 * @param enabled       是否启用
 * @param workshopScope 车间数据域，管理员为 null
 * @param createdAt     创建时间
 * @author Caspian
 */
public record UserView(Long id, String username, String nickname, String role, boolean enabled,
                       String workshopScope, LocalDateTime createdAt) {

    /**
     * 由实体转换为视图。
     *
     * @param user 用户实体
     * @return 脱敏后的视图
     */
    public static UserView of(SysUser user) {
        return new UserView(user.getId(), user.getUsername(), user.getNickname(), user.getRole(),
                Integer.valueOf(1).equals(user.getEnabled()), user.getWorkshopScope(), user.getCreatedAt());
    }
}
