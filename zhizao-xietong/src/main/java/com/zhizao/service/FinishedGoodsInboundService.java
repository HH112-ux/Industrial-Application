package com.zhizao.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhizao.common.BadRequestException;
import com.zhizao.common.BusinessException;
import com.zhizao.common.PageResult;
import com.zhizao.entity.FinishedGoodsInbound;
import com.zhizao.entity.WorkOrder;
import com.zhizao.enums.WorkOrderStatus;
import com.zhizao.mapper.FinishedGoodsInboundMapper;
import com.zhizao.security.DataScope;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

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
    private final DataScope dataScope;

    public FinishedGoodsInboundService(FinishedGoodsInboundMapper inboundMapper, DataScope dataScope) {
        this.inboundMapper = inboundMapper;
        this.dataScope = dataScope;
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

    /**
     * 分页查询入库单。非管理员只能看到自己车间范围；未配置车间时返回空列表，而不是报错。
     */
    public PageResult<FinishedGoodsInbound> list(int pageNum, int pageSize, String keyword,
                                                 String status, String workshop) {
        Page<FinishedGoodsInbound> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<FinishedGoodsInbound> qw = new LambdaQueryWrapper<>();
        qw.and(StringUtils.hasText(keyword), w -> w
                .like(FinishedGoodsInbound::getProductCode, keyword)
                .or().like(FinishedGoodsInbound::getBatch, keyword));
        qw.eq(StringUtils.hasText(status), FinishedGoodsInbound::getStatus, status);
        qw.eq(StringUtils.hasText(workshop), FinishedGoodsInbound::getWorkshop, workshop);
        String scope = dataScope.workshopFilter();
        qw.eq(scope != null, FinishedGoodsInbound::getWorkshop, scope);
        qw.orderByDesc(FinishedGoodsInbound::getId);
        inboundMapper.selectPage(page, qw);
        return PageResult.of(page.getTotal(), page.getRecords());
    }

    /**
     * 取消待入库单：原因必填，且只有「待入库」可以取消。状态更新使用条件更新
     * {@code WHERE status='PENDING'}，命中 0 行说明已被确认或取消，返回业务码 409。
     */
    @Transactional
    public FinishedGoodsInbound cancel(Long id, String reason, String operator) {
        String trimmed = reason == null ? "" : reason.trim();
        if (trimmed.isEmpty()) {
            throw new BadRequestException("取消原因不能为空");
        }
        if (trimmed.length() > 255) {
            throw new BadRequestException("取消原因不能超过 255 字");
        }
        FinishedGoodsInbound record = inboundMapper.selectById(id);
        if (record == null) {
            throw new BusinessException("入库单不存在");
        }
        dataScope.requireWorkshop(record.getWorkshop());
        int updated = inboundMapper.update(null, new LambdaUpdateWrapper<FinishedGoodsInbound>()
                .eq(FinishedGoodsInbound::getId, id)
                .eq(FinishedGoodsInbound::getStatus, FinishedGoodsInbound.STATUS_PENDING)
                .set(FinishedGoodsInbound::getStatus, FinishedGoodsInbound.STATUS_CANCELLED)
                .set(FinishedGoodsInbound::getCancelReason, trimmed)
                .set(FinishedGoodsInbound::getOperator, operator));
        if (updated == 0) {
            throw new BusinessException(409, "入库单已确认或已取消，请刷新后重试");
        }
        return inboundMapper.selectById(id);
    }

    private static int qtyValue(Integer qty) {
        return qty == null ? 0 : qty;
    }
}
