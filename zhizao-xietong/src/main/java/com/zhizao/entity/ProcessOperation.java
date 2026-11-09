package com.zhizao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 工艺工序：某个工艺版本下按顺序执行的一道工序。
 *
 * @author Caspian
 */
@TableName("pr_process_operation")
public class ProcessOperation {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long processId;
    private Integer seq;
    private String operationName;
    private String deviceType;
    private BigDecimal estHours;
    private BigDecimal setupHours;
    private LocalDateTime createdAt;
    @TableLogic
    private Integer deleted;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getProcessId() { return processId; }
    public void setProcessId(Long processId) { this.processId = processId; }
    public Integer getSeq() { return seq; }
    public void setSeq(Integer seq) { this.seq = seq; }
    public String getOperationName() { return operationName; }
    public void setOperationName(String operationName) { this.operationName = operationName; }
    public String getDeviceType() { return deviceType; }
    public void setDeviceType(String deviceType) { this.deviceType = deviceType; }
    public BigDecimal getEstHours() { return estHours; }
    public void setEstHours(BigDecimal estHours) { this.estHours = estHours; }
    public BigDecimal getSetupHours() { return setupHours; }
    public void setSetupHours(BigDecimal setupHours) { this.setupHours = setupHours; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public Integer getDeleted() { return deleted; }
    public void setDeleted(Integer deleted) { this.deleted = deleted; }
}
