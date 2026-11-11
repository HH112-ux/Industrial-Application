package com.zhizao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhizao.entity.FinishedGoodsInbound;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

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

    /**
     * 条件确认入库：只有待入库、且数量不超过 {@code min(计划数, 合格数) - 已入库数} 时才更新。
     *
     * <p>已入库数通过同表派生表汇总，避免与更新目标表直接子查询冲突；两个条件与状态位
     * 同时写在 WHERE 中，命中 0 行即代表并发下已被处理或数量超额。</p>
     *
     * @param id       入库单主键
     * @param batch    批次号覆盖值，空串表示沿用原批次
     * @param operator 操作人
     * @return 受影响行数，1 表示确认成功，0 表示条件不满足
     */
    @Update("UPDATE fg_inbound_order i "
            + "JOIN wo_work_order w ON w.id = i.work_order_id "
            + "LEFT JOIN (SELECT work_order_id, SUM(qty) AS inbound_qty FROM fg_inbound_order "
            + "           WHERE status = 'INBOUND' GROUP BY work_order_id) s "
            + "       ON s.work_order_id = i.work_order_id "
            + "SET i.status = 'INBOUND', "
            + "    i.batch = COALESCE(NULLIF(#{batch}, ''), i.batch), "
            + "    i.operator = #{operator}, "
            + "    i.inbound_time = NOW() "
            + "WHERE i.id = #{id} AND i.status = 'PENDING' "
            + "  AND i.qty + COALESCE(s.inbound_qty, 0) <= LEAST(w.qty, COALESCE(w.qty_qualified, 0))")
    int confirm(@Param("id") Long id, @Param("batch") String batch, @Param("operator") String operator);

    /**
     * 以当前读（加锁）重新读取单据，用于条件更新命中 0 行后区分失败原因。
     *
     * <p>普通 SELECT 在可重复读隔离级别下会复用事务快照，可能读不到并发事务刚提交的状态。</p>
     *
     * @param id 入库单主键
     * @return 最新已提交的单据，不存在时返回 null
     */
    @Select("SELECT * FROM fg_inbound_order WHERE id = #{id} FOR UPDATE")
    FinishedGoodsInbound selectByIdForUpdate(@Param("id") Long id);
}
