package com.zhizao.service;

import com.zhizao.entity.FinishedGoodsInbound;
import com.zhizao.entity.WorkOrder;
import com.zhizao.enums.WorkOrderStatus;
import com.zhizao.mapper.FinishedGoodsInboundMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 成品入库服务：工单质检通过并完工后自动生成待入库单。
 *
 * <p>入库数量 = min(工单计划数量, 质检通过数量) - 已入库数，保证不超过计划数与合格数。
 * 工单维度幂等由 {@code uk_inbound_work_order} 唯一键兜底，重复触发直接忽略。</p>
 *
 * @author Caspian
 */
@Service
public class FinishedGoodsInboundService {

    private static final DateTimeFormatter BATCH_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final FinishedGoodsInboundMapper inboundMapper;

    public FinishedGoodsInboundService(FinishedGoodsInboundMapper inboundMapper) {
        this.inboundMapper = inboundMapper;
    }

    /**
     * 生成待入库单，与完工动作处于同一事务。
     *
     * <p>数量取计划数量与合格数的较小值减去已入库数，为 0 时不生成空单。
     * 批次号默认「工单号-yyyyMMdd」，确认入库时可由前端覆盖。
     * 唯一键冲突说明该工单已生成过入库单（含已取消），直接忽略，不覆盖历史状态。</p>
     *
     * @param order 已完成质检并流转为「已完工」的工单
     */
    @Transactional
    public void generateFor(WorkOrder order) {
        if (!WorkOrderStatus.FINISHED.name().equals(order.getStatus())) {
            return;
        }
        Long inbound = inboundMapper.sumInboundQty(order.getId());
        int alreadyInbound = inbound == null ? 0 : inbound.intValue();
        int qty = Math.min(qtyValue(order.getQty()), qtyValue(order.getQtyQualified())) - alreadyInbound;
        if (qty <= 0) {
            return;
        }
        FinishedGoodsInbound record = new FinishedGoodsInbound();
        record.setWorkOrderId(order.getId());
        record.setProductCode(order.getProductCode());
        record.setQty(qty);
        record.setBatch(order.getOrderNo() + "-" + LocalDate.now().format(BATCH_DATE));
        record.setWorkshop(order.getWorkshop());
        record.setStatus(FinishedGoodsInbound.STATUS_PENDING);
        try {
            inboundMapper.insert(record);
        } catch (DuplicateKeyException ignored) {
            // 工单维度幂等：已有入库单（待入库/已入库/已取消）时不再生成
        }
    }

    private static int qtyValue(Integer qty) {
        return qty == null ? 0 : qty;
    }
}
