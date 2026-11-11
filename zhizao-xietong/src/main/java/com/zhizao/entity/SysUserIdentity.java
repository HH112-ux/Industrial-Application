package com.zhizao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 外部身份映射：一个外部身份只对应一个本地账号，同一提供方下也只有一个身份。
 *
 * <p>外部唯一标识只认企微 userid 这类稳定值；姓名、手机号、邮箱与部门仅作快照展示，
 * 不产生任何授权值。</p>
 *
 * @author Caspian
 */
@TableName("sys_user_identity")
public class SysUserIdentity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String provider;
    private String externalId;
    private String externalUsername;
    private String displayName;
    private String mobile;
    private String email;
    private String departmentIds;
    private LocalDateTime lastLoginAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getExternalId() { return externalId; }
    public void setExternalId(String externalId) { this.externalId = externalId; }
    public String getExternalUsername() { return externalUsername; }
    public void setExternalUsername(String externalUsername) { this.externalUsername = externalUsername; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getMobile() { return mobile; }
    public void setMobile(String mobile) { this.mobile = mobile; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getDepartmentIds() { return departmentIds; }
    public void setDepartmentIds(String departmentIds) { this.departmentIds = departmentIds; }
    public LocalDateTime getLastLoginAt() { return lastLoginAt; }
    public void setLastLoginAt(LocalDateTime lastLoginAt) { this.lastLoginAt = lastLoginAt; }
}
