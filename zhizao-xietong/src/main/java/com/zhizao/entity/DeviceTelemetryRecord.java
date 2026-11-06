package com.zhizao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 设备遥测时序记录：一次设备上报的原始数值与服务端接收时间。
 *
 * @author Caspian
 */
@TableName("eq_telemetry")
public class DeviceTelemetryRecord {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long deviceId;
    private String deviceNo;
    private String status;
    private Double temperature;
    private Integer rpm;
    private Double current;
    private LocalDateTime reportedAt;
    private LocalDateTime receivedAt;
    private String ingestKey;
    private String source;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getDeviceId() { return deviceId; }
    public void setDeviceId(Long deviceId) { this.deviceId = deviceId; }
    public String getDeviceNo() { return deviceNo; }
    public void setDeviceNo(String deviceNo) { this.deviceNo = deviceNo; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Double getTemperature() { return temperature; }
    public void setTemperature(Double temperature) { this.temperature = temperature; }
    public Integer getRpm() { return rpm; }
    public void setRpm(Integer rpm) { this.rpm = rpm; }
    public Double getCurrent() { return current; }
    public void setCurrent(Double current) { this.current = current; }
    public LocalDateTime getReportedAt() { return reportedAt; }
    public void setReportedAt(LocalDateTime reportedAt) { this.reportedAt = reportedAt; }
    public LocalDateTime getReceivedAt() { return receivedAt; }
    public void setReceivedAt(LocalDateTime receivedAt) { this.receivedAt = receivedAt; }
    public String getIngestKey() { return ingestKey; }
    public void setIngestKey(String ingestKey) { this.ingestKey = ingestKey; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
}
