package com.zhizao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 设备报警规则：同一设备 + 指标 + 规则类型只允许一条，由业务校验与唯一键保证。
 *
 * @author Caspian
 */
@TableName("eq_alarm_rule")
public class AlarmRule {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long deviceId;
    private String metric;
    private String ruleType;
    private Double upperLimit;
    private Double lowerLimit;
    private Integer consecutiveTimes;
    private Integer currentStreak;
    private Boolean enabled;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getDeviceId() { return deviceId; }
    public void setDeviceId(Long deviceId) { this.deviceId = deviceId; }
    public String getMetric() { return metric; }
    public void setMetric(String metric) { this.metric = metric; }
    public String getRuleType() { return ruleType; }
    public void setRuleType(String ruleType) { this.ruleType = ruleType; }
    public Double getUpperLimit() { return upperLimit; }
    public void setUpperLimit(Double upperLimit) { this.upperLimit = upperLimit; }
    public Double getLowerLimit() { return lowerLimit; }
    public void setLowerLimit(Double lowerLimit) { this.lowerLimit = lowerLimit; }
    public Integer getConsecutiveTimes() { return consecutiveTimes; }
    public void setConsecutiveTimes(Integer consecutiveTimes) { this.consecutiveTimes = consecutiveTimes; }
    public Integer getCurrentStreak() { return currentStreak; }
    public void setCurrentStreak(Integer currentStreak) { this.currentStreak = currentStreak; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
}
