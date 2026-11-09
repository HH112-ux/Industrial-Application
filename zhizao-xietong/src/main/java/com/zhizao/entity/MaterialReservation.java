package com.zhizao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 物料预占：工单开工时按 BOM 需求量占用库存，完工时转为实际消耗并扣减库存。
 *
 * <p>齐套判断按「可用量 = 库存 − 该物料的 RESERVED 预占总量」核算，
 * 同一批库存不会被多张工单重复通过。</p>
 *
 * @author Caspian
 */
@TableName("mt_material_reservation")
public class MaterialReservation {

    public static final String STATUS_RESERVED = "RESERVED";
    public static final String STATUS_CONSUMED = "CONSUMED";

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long workOrderId;
    private String matCode;
    private Integer reservedQty;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getWorkOrderId() { return workOrderId; }
    public void setWorkOrderId(Long workOrderId) { this.workOrderId = workOrderId; }
    public String getMatCode() { return matCode; }
    public void setMatCode(String matCode) { this.matCode = matCode; }
    public Integer getReservedQty() { return reservedQty; }
    public void setReservedQty(Integer reservedQty) { this.reservedQty = reservedQty; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
