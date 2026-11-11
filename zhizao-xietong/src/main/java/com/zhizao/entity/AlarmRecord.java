package com.zhizao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/** 设备报警记录：未恢复期间同设备+指标+规则类型唯一，恢复后 activeFlag 置空。 */
@TableName("eq_alarm_record")
public class AlarmRecord {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long deviceId;
    private String metric;
    private String ruleType;
    private Double triggerValue;
    private String thresholdSnapshot;
    private LocalDateTime startTime;
    private LocalDateTime recoverTime;
    private String status;
    private String ackUser;
    private LocalDateTime ackTime;
    private Integer activeFlag;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getDeviceId() { return deviceId; }
    public void setDeviceId(Long deviceId) { this.deviceId = deviceId; }
    public String getMetric() { return metric; }
    public void setMetric(String metric) { this.metric = metric; }
    public String getRuleType() { return ruleType; }
    public void setRuleType(String ruleType) { this.ruleType = ruleType; }
    public Double getTriggerValue() { return triggerValue; }
    public void setTriggerValue(Double triggerValue) { this.triggerValue = triggerValue; }
    public String getThresholdSnapshot() { return thresholdSnapshot; }
    public void setThresholdSnapshot(String thresholdSnapshot) { this.thresholdSnapshot = thresholdSnapshot; }
    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
    public LocalDateTime getRecoverTime() { return recoverTime; }
    public void setRecoverTime(LocalDateTime recoverTime) { this.recoverTime = recoverTime; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getAckUser() { return ackUser; }
    public void setAckUser(String ackUser) { this.ackUser = ackUser; }
    public LocalDateTime getAckTime() { return ackTime; }
    public void setAckTime(LocalDateTime ackTime) { this.ackTime = ackTime; }
    public Integer getActiveFlag() { return activeFlag; }
    public void setActiveFlag(Integer activeFlag) { this.activeFlag = activeFlag; }
}
