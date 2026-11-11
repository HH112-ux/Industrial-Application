package com.zhizao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhizao.entity.FinishedGoodsStock;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

/**
 * 成品库存 Mapper。
 *
 * @author Caspian
 */
public interface FinishedGoodsStockMapper extends BaseMapper<FinishedGoodsStock> {

    /**
     * 按成品编码 + 车间原子累加库存，依赖唯一键 {@code uk_stock_product_workshop}。
     *
     * @param productCode 成品编码
     * @param workshop    车间
     * @param qty         本次入库数量
     * @return 受影响行数
     */
    @Insert("INSERT INTO fg_finished_stock (product_code, workshop, qty) "
            + "VALUES (#{productCode}, #{workshop}, #{qty}) "
            + "ON DUPLICATE KEY UPDATE qty = qty + #{qty}")
    int accumulate(@Param("productCode") String productCode,
                   @Param("workshop") String workshop,
                   @Param("qty") int qty);
}
