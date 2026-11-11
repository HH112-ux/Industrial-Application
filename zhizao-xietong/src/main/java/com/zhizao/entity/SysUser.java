package com.zhizao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("sys_user")
public class SysUser {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    private String password;
    private String nickname;
    private String role;
    private Integer enabled;
    private Integer tokenVersion;
    private Integer mustChangePassword;
    private String workshopScope;
    /** 账号来源：LOCAL 为本地密码账号，SSO 为统一身份建号，SSO 账号禁止本地密码登录。 */
    private String authSource;
    private LocalDateTime createdAt;
    @TableLogic
    private Integer deleted;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public Integer getEnabled() { return enabled; }
    public void setEnabled(Integer enabled) { this.enabled = enabled; }
    public Integer getTokenVersion() { return tokenVersion; }
    public void setTokenVersion(Integer tokenVersion) { this.tokenVersion = tokenVersion; }
    public Integer getMustChangePassword() { return mustChangePassword; }
    public void setMustChangePassword(Integer mustChangePassword) { this.mustChangePassword = mustChangePassword; }
    public String getWorkshopScope() { return workshopScope; }
    public void setWorkshopScope(String workshopScope) { this.workshopScope = workshopScope; }
    public String getAuthSource() { return authSource; }
    public void setAuthSource(String authSource) { this.authSource = authSource; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public Integer getDeleted() { return deleted; }
    public void setDeleted(Integer deleted) { this.deleted = deleted; }
}
