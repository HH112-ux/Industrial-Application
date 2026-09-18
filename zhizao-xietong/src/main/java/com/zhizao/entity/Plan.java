package com.zhizao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@TableName("po_plan")
public class Plan {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String planOrderNo;
    private String productCode;
    private String productName;
    private Integer planQty;
    private LocalDate planStart;
    private LocalDate planEnd;
    private String priority;
    private String batch;
    private Long requiredDeviceId;
    private BigDecimal estHours;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer deleted;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getPlanOrderNo() { return planOrderNo; }
    public void setPlanOrderNo(String planOrderNo) { this.planOrderNo = planOrderNo; }
    public String getProductCode() { return productCode; }
    public void setProductCode(String productCode) { this.productCode = productCode; }
    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }
    public Integer getPlanQty() { return planQty; }
    public void setPlanQty(Integer planQty) { this.planQty = planQty; }
    public LocalDate getPlanStart() { return planStart; }
    public void setPlanStart(LocalDate planStart) { this.planStart = planStart; }
    public LocalDate getPlanEnd() { return planEnd; }
    public void setPlanEnd(LocalDate planEnd) { this.planEnd = planEnd; }
    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }
    public String getBatch() { return batch; }
    public void setBatch(String batch) { this.batch = batch; }
    public Long getRequiredDeviceId() { return requiredDeviceId; }
    public void setRequiredDeviceId(Long requiredDeviceId) { this.requiredDeviceId = requiredDeviceId; }
    public BigDecimal getEstHours() { return estHours; }
    public void setEstHours(BigDecimal estHours) { this.estHours = estHours; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public Integer getDeleted() { return deleted; }
    public void setDeleted(Integer deleted) { this.deleted = deleted; }
}
