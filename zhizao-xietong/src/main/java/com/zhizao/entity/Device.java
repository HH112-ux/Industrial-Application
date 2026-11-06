package com.zhizao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 设备台账：保存设备基础信息、运行状态与最近一次上报时间。
 *
 * @author Caspian
 */
@TableName("eq_device")
public class Device {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String deviceNo;
    private String deviceName;
    private String deviceType;
    private String manufacturer;
    private String model;
    private String serialNo;
    private String status;
    private LocalDateTime lastSeenAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer deleted;

    /** 在线标记，按最近上报时间实时计算，不落库。 */
    @TableField(exist = false)
    private Boolean online;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getDeviceNo() { return deviceNo; }
    public void setDeviceNo(String deviceNo) { this.deviceNo = deviceNo; }
    public String getDeviceName() { return deviceName; }
    public void setDeviceName(String deviceName) { this.deviceName = deviceName; }
    public String getDeviceType() { return deviceType; }
    public void setDeviceType(String deviceType) { this.deviceType = deviceType; }
    public String getManufacturer() { return manufacturer; }
    public void setManufacturer(String manufacturer) { this.manufacturer = manufacturer; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public String getSerialNo() { return serialNo; }
    public void setSerialNo(String serialNo) { this.serialNo = serialNo; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    /**
     * 获取最近一次有效上报时间。
     *
     * @return 最近上报时间，从未上报返回 null
     */
    public LocalDateTime getLastSeenAt() { return lastSeenAt; }

    /**
     * 设置最近一次有效上报时间。
     *
     * @param lastSeenAt 最近上报时间
     */
    public void setLastSeenAt(LocalDateTime lastSeenAt) { this.lastSeenAt = lastSeenAt; }

    /**
     * 获取在线标记。
     *
     * @return true 表示在在线阈值内收到过上报
     */
    public Boolean getOnline() { return online; }

    /**
     * 设置在线标记。
     *
     * @param online 在线标记，不落库
     */
    public void setOnline(Boolean online) { this.online = online; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public Integer getDeleted() { return deleted; }
    public void setDeleted(Integer deleted) { this.deleted = deleted; }
}
