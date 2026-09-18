package com.zhizao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("wo_quality")
public class QualityRecord {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String qualityNo;
    private Long orderId;
    private String inspector;
    private String result;
    private String defectDesc;
    private LocalDateTime inspectTime;
    private LocalDateTime createdAt;
    @TableLogic
    private Integer deleted;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getQualityNo() { return qualityNo; }
    public void setQualityNo(String qualityNo) { this.qualityNo = qualityNo; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public String getInspector() { return inspector; }
    public void setInspector(String inspector) { this.inspector = inspector; }
    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }
    public String getDefectDesc() { return defectDesc; }
    public void setDefectDesc(String defectDesc) { this.defectDesc = defectDesc; }
    public LocalDateTime getInspectTime() { return inspectTime; }
    public void setInspectTime(LocalDateTime inspectTime) { this.inspectTime = inspectTime; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public Integer getDeleted() { return deleted; }
    public void setDeleted(Integer deleted) { this.deleted = deleted; }
}
