package com.zhizao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 报工记录：一次数量上报的留档，携带客户端幂等键用于弱网重试去重。
 *
 * @author Caspian
 */
@TableName("wo_report")
public class WorkOrderReport {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String reportNo;
    private String idempotencyKey;
    private Long orderId;
    private LocalDateTime reportTime;
    private Integer qty;
    private Long deviceId;
    private String operator;
    private Integer qualifiedQty;
    private Integer defectQty;
    private LocalDateTime createdAt;
    @TableLogic
    private Integer deleted;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getReportNo() { return reportNo; }
    public void setReportNo(String reportNo) { this.reportNo = reportNo; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public LocalDateTime getReportTime() { return reportTime; }
    public void setReportTime(LocalDateTime reportTime) { this.reportTime = reportTime; }
    public Integer getQty() { return qty; }
    public void setQty(Integer qty) { this.qty = qty; }
    public Long getDeviceId() { return deviceId; }
    public void setDeviceId(Long deviceId) { this.deviceId = deviceId; }
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
    public Integer getQualifiedQty() { return qualifiedQty; }
    public void setQualifiedQty(Integer qualifiedQty) { this.qualifiedQty = qualifiedQty; }
    public Integer getDefectQty() { return defectQty; }
    public void setDefectQty(Integer defectQty) { this.defectQty = defectQty; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public Integer getDeleted() { return deleted; }
    public void setDeleted(Integer deleted) { this.deleted = deleted; }
}
