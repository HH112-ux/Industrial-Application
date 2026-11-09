package com.zhizao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDateTime;

/**
 * 生产工单：一次排产下达的执行单元，携带状态与累计报工数量。
 *
 * <p>`version` 用于乐观锁，同一工单的并发流转只有一次能成功；累计数量用于报工上限校验。</p>
 *
 * @author Caspian
 */
@TableName("wo_work_order")
public class WorkOrder {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String orderNo;
    private Long planId;
    private String productCode;
    private String productName;
    private Integer qty;
    private Integer qtyDone;
    private Integer qtyQualified;
    private Integer qtyDefect;
    private Integer qtyRework;
    private String batch;
    private String workshop;
    private Long deviceId;
    private Long processId;
    private Integer processVersion;
    private String status;
    @Version
    private Integer version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer deleted;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public Long getPlanId() { return planId; }
    public void setPlanId(Long planId) { this.planId = planId; }
    public String getProductCode() { return productCode; }
    public void setProductCode(String productCode) { this.productCode = productCode; }
    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }
    public Integer getQty() { return qty; }
    public void setQty(Integer qty) { this.qty = qty; }
    public Integer getQtyDone() { return qtyDone; }
    public void setQtyDone(Integer qtyDone) { this.qtyDone = qtyDone; }
    public Integer getQtyQualified() { return qtyQualified; }
    public void setQtyQualified(Integer qtyQualified) { this.qtyQualified = qtyQualified; }
    public Integer getQtyDefect() { return qtyDefect; }
    public void setQtyDefect(Integer qtyDefect) { this.qtyDefect = qtyDefect; }
    public Integer getQtyRework() { return qtyRework; }
    public void setQtyRework(Integer qtyRework) { this.qtyRework = qtyRework; }
    public String getBatch() { return batch; }
    public void setBatch(String batch) { this.batch = batch; }
    public String getWorkshop() { return workshop; }
    public void setWorkshop(String workshop) { this.workshop = workshop; }
    public Long getDeviceId() { return deviceId; }
    public void setDeviceId(Long deviceId) { this.deviceId = deviceId; }
    public Long getProcessId() { return processId; }
    public void setProcessId(Long processId) { this.processId = processId; }
    public Integer getProcessVersion() { return processVersion; }
    public void setProcessVersion(Integer processVersion) { this.processVersion = processVersion; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public Integer getDeleted() { return deleted; }
    public void setDeleted(Integer deleted) { this.deleted = deleted; }
}
