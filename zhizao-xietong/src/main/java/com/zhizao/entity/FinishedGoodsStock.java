package com.zhizao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 成品库存：按成品编码 + 车间累计合格入库数量，确认入库时以原子累加方式维护。
 *
 * <p>唯一键 {@code uk_stock_product_workshop} 保证同一编码同一车间只有一行，
 * 累加通过 {@code ON DUPLICATE KEY UPDATE} 完成，不做先查后写。</p>
 *
 * @author Caspian
 */
@TableName("fg_finished_stock")
public class FinishedGoodsStock {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String productCode;
    private String workshop;
    private Integer qty;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getProductCode() { return productCode; }
    public void setProductCode(String productCode) { this.productCode = productCode; }
    public String getWorkshop() { return workshop; }
    public void setWorkshop(String workshop) { this.workshop = workshop; }
    public Integer getQty() { return qty; }
    public void setQty(Integer qty) { this.qty = qty; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
