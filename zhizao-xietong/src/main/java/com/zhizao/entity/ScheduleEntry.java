package com.zhizao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 排程结果：一张工单的一道工序在某个设备上的时间窗，或无法排入时的业务码与原因。
 *
 * <p>该表是派生数据，重排时只重建「待排」工单的结果；在产与已完工工单的记录保留，
 * 既作为已占用产能，也用于回答「这张工单为什么排在这个时间」。</p>
 *
 * @author Caspian
 */
@TableName("po_schedule_entry")
public class ScheduleEntry {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long workOrderId;
    private Long planId;
    private Integer operationSeq;
    private String operationName;
    private String deviceType;
    private Long deviceId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private BigDecimal durationHours;
    private Integer estimated;
    private Integer queueRank;
    private String rankBasis;
    private String status;
    private String conflictCode;
    private String message;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getWorkOrderId() { return workOrderId; }
    public void setWorkOrderId(Long workOrderId) { this.workOrderId = workOrderId; }
    public Long getPlanId() { return planId; }
    public void setPlanId(Long planId) { this.planId = planId; }
    public Integer getOperationSeq() { return operationSeq; }
    public void setOperationSeq(Integer operationSeq) { this.operationSeq = operationSeq; }
    public String getOperationName() { return operationName; }
    public void setOperationName(String operationName) { this.operationName = operationName; }
    public String getDeviceType() { return deviceType; }
    public void setDeviceType(String deviceType) { this.deviceType = deviceType; }
    public Long getDeviceId() { return deviceId; }
    public void setDeviceId(Long deviceId) { this.deviceId = deviceId; }
    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
    public BigDecimal getDurationHours() { return durationHours; }
    public void setDurationHours(BigDecimal durationHours) { this.durationHours = durationHours; }
    public Integer getEstimated() { return estimated; }
    public void setEstimated(Integer estimated) { this.estimated = estimated; }
    public Integer getQueueRank() { return queueRank; }
    public void setQueueRank(Integer queueRank) { this.queueRank = queueRank; }
    public String getRankBasis() { return rankBasis; }
    public void setRankBasis(String rankBasis) { this.rankBasis = rankBasis; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getConflictCode() { return conflictCode; }
    public void setConflictCode(String conflictCode) { this.conflictCode = conflictCode; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
