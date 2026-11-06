package com.zhizao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 工艺物料清单（BOM）项，挂在工艺版本行（{@code processId}）下。
 *
 * @author Caspian
 */
@TableName("pr_bom")
public class BomItem {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long processId;
    /**
     * 历史遗留字段：BOM 版本已由工艺版本行表达，新逻辑不再按此列读写。
     *
     * @deprecated 使用 {@code processId} 定位工艺版本，保留该列仅为兼容存量数据。
     */
    @Deprecated
    private Integer version = 1;
    private String matCode;
    private Integer requiredQty;
    private LocalDateTime createdAt;
    @TableLogic
    private Integer deleted;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getProcessId() { return processId; }
    public void setProcessId(Long processId) { this.processId = processId; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public String getMatCode() { return matCode; }
    public void setMatCode(String matCode) { this.matCode = matCode; }
    public Integer getRequiredQty() { return requiredQty; }
    public void setRequiredQty(Integer requiredQty) { this.requiredQty = requiredQty; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public Integer getDeleted() { return deleted; }
    public void setDeleted(Integer deleted) { this.deleted = deleted; }
}
