package com.zhizao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhizao.entity.FinishedGoodsInbound;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 成品入库单数据访问。
 *
 * @author Caspian
 */
public interface FinishedGoodsInboundMapper extends BaseMapper<FinishedGoodsInbound> {

    /**
     * 统计工单已确认入库的数量，用于计算本次待入库数量。
     *
     * @param workOrderId 工单主键
     * @return 已入库数量，没有记录时返回 0
     */
    @Select("SELECT COALESCE(SUM(qty), 0) FROM fg_inbound_order "
            + "WHERE work_order_id = #{workOrderId} AND status = 'INBOUND'")
    Long sumInboundQty(@Param("workOrderId") Long workOrderId);
}
