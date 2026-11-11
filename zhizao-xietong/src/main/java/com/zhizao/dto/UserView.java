package com.zhizao.dto;

import com.zhizao.entity.SysUser;
import com.zhizao.entity.SysUserIdentity;

import java.time.LocalDateTime;

/**
 * 用户列表视图：不包含密码等敏感字段。
 *
 * @param id             用户主键
 * @param username       用户名
 * @param nickname       昵称
 * @param role           角色
 * @param enabled        是否启用
 * @param workshopScope  车间数据域，管理员为 null
 * @param authSource     账号来源：LOCAL 本地，SSO 仅企微，BOTH 双通道
 * @param departmentIds  企微部门 ID 快照，仅作车间分配的建议依据，不是授权值
 * @param createdAt      创建时间
 * @author Caspian
 */
public record UserView(Long id, String username, String nickname, String role, boolean enabled,
                       String workshopScope, String authSource, String departmentIds, LocalDateTime createdAt) {

    /**
     * 由实体转换为视图，不含外部身份快照。
     *
     * @param user 用户实体
     * @return 脱敏后的视图
     */
    public static UserView of(SysUser user) {
        return of(user, null);
    }

    /**
     * 由实体与外部身份绑定转换为视图。
     *
     * @param user     用户实体
     * @param identity 外部身份绑定，本地账号为 null
     * @return 脱敏后的视图
     */
    public static UserView of(SysUser user, SysUserIdentity identity) {
        return new UserView(user.getId(), user.getUsername(), user.getNickname(), user.getRole(),
                Integer.valueOf(1).equals(user.getEnabled()), user.getWorkshopScope(), user.getAuthSource(),
                identity == null ? null : identity.getDepartmentIds(), user.getCreatedAt());
    }
}
