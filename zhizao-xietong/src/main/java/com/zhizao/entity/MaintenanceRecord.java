package com.zhizao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("eq_maintenance")
public class MaintenanceRecord {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String mtNo;
    private Long deviceId;
    private String mtType;
    private String operator;
    private LocalDateTime mtTime;
    private String description;
    private String resultStatus;
    private LocalDateTime createdAt;
    @TableLogic
    private Integer deleted;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getMtNo() { return mtNo; }
    public void setMtNo(String mtNo) { this.mtNo = mtNo; }
    public Long getDeviceId() { return deviceId; }
    public void setDeviceId(Long deviceId) { this.deviceId = deviceId; }
    public String getMtType() { return mtType; }
    public void setMtType(String mtType) { this.mtType = mtType; }
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
    public LocalDateTime getMtTime() { return mtTime; }
    public void setMtTime(LocalDateTime mtTime) { this.mtTime = mtTime; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getResultStatus() { return resultStatus; }
    public void setResultStatus(String resultStatus) { this.resultStatus = resultStatus; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public Integer getDeleted() { return deleted; }
    public void setDeleted(Integer deleted) { this.deleted = deleted; }
}
