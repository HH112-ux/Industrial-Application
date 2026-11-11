package com.zhizao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 认证事件：登录、登出与 SSO 换票的结果留痕，与业务操作审计分表。
 *
 * <p>只保存事件类型、结果码与失败分类，不保存请求体，口令/授权码/state 不会进入本表。</p>
 *
 * @author Caspian
 */
@TableName("sys_auth_event")
public class AuthEvent {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String eventType;
    private String username;
    private Boolean success;
    private String resultCode;
    private String reason;
    private String ip;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public Boolean getSuccess() { return success; }
    public void setSuccess(Boolean success) { this.success = success; }
    public String getResultCode() { return resultCode; }
    public void setResultCode(String resultCode) { this.resultCode = resultCode; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getIp() { return ip; }
    public void setIp(String ip) { this.ip = ip; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
