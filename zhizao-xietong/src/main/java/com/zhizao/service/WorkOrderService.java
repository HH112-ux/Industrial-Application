package com.zhizao.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhizao.common.BadRequestException;
import com.zhizao.common.BusinessException;
import com.zhizao.common.PageResult;
import com.zhizao.dto.QcRequest;
import com.zhizao.dto.ReportRequest;
import com.zhizao.entity.Plan;
import com.zhizao.entity.Process;
import com.zhizao.entity.QualityRecord;
import com.zhizao.entity.WorkOrder;
import com.zhizao.entity.WorkOrderReport;
import com.zhizao.entity.WorkOrderStatusHistory;
import com.zhizao.enums.ApprovalStatus;
import com.zhizao.enums.PlanStatus;
import com.zhizao.enums.WorkOrderStatus;
import com.zhizao.mapper.PlanMapper;
import com.zhizao.mapper.QualityRecordMapper;
import com.zhizao.mapper.WorkOrderMapper;
import com.zhizao.mapper.WorkOrderReportMapper;
import com.zhizao.mapper.WorkOrderStatusHistoryMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 生产工单服务：负责开工、报工、质检三条执行链路，以及状态流转历史查询。
 *
 * <p>每条链路都在同一事务内完成状态更新与历史留痕。状态更新使用乐观锁版本号，
 * 影响行数为 0 表示工单已被其他请求改动，按接口契约返回业务码 409，由前端提示刷新。</p>
 *
 * @author Caspian
 */
@Service
public class WorkOrderService {

    private final WorkOrderMapper workOrderMapper;
    private final WorkOrderReportMapper workOrderReportMapper;
    private final QualityRecordMapper qualityRecordMapper;
    private final PlanMapper planMapper;
    private final WorkOrderStatusHistoryMapper statusHistoryMapper;
    private final WorkOrderStateMachine stateMachine;
    private final KitCheckService kitCheckService;
    private final ProcessService processService;
    private final CodeGenerator codeGenerator;

    /**
     * 构造工单服务。
     *
     * @param workOrderMapper      工单数据访问
     * @param workOrderReportMapper 报工数据访问
     * @param qualityRecordMapper  质检数据访问
     * @param planMapper           计划数据访问
     * @param statusHistoryMapper  状态流转历史数据访问
     * @param stateMachine         工单状态机
     * @param kitCheckService      齐套校验服务
     * @param processService       工艺服务
     * @param codeGenerator        业务编号生成器
     */
    public WorkOrderService(WorkOrderMapper workOrderMapper, WorkOrderReportMapper workOrderReportMapper,
                            QualityRecordMapper qualityRecordMapper, PlanMapper planMapper,
                            WorkOrderStatusHistoryMapper statusHistoryMapper, WorkOrderStateMachine stateMachine,
                            KitCheckService kitCheckService, ProcessService processService,
                            CodeGenerator codeGenerator) {
        this.workOrderMapper = workOrderMapper;
        this.workOrderReportMapper = workOrderReportMapper;
        this.qualityRecordMapper = qualityRecordMapper;
        this.planMapper = planMapper;
        this.statusHistoryMapper = statusHistoryMapper;
        this.stateMachine = stateMachine;
        this.kitCheckService = kitCheckService;
        this.processService = processService;
        this.codeGenerator = codeGenerator;
    }

    public PageResult<WorkOrder> list(int pageNum, int pageSize, String keyword, String status) {
        Page<WorkOrder> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<WorkOrder> qw = new LambdaQueryWrapper<>();
        qw.and(StringUtils.hasText(keyword), w -> w
                .like(WorkOrder::getOrderNo, keyword)
                .or().like(WorkOrder::getProductCode, keyword)
                .or().like(WorkOrder::getProductName, keyword)
                .or().like(WorkOrder::getWorkshop, keyword));
        qw.eq(StringUtils.hasText(status), WorkOrder::getStatus, status);
        qw.orderByDesc(WorkOrder::getId);
        workOrderMapper.selectPage(page, qw);
        return PageResult.of(page.getTotal(), page.getRecords());
    }

    public WorkOrder getById(Long id) {
        WorkOrder order = workOrderMapper.selectById(id);
        if (order == null) {
            throw new BusinessException("工单不存在");
        }
        return order;
    }

    /**
     * 查询工单状态流转历史，按发生时间升序返回。
     *
     * @param id 工单主键
     * @return 按发生时间升序排列的历史记录
     * @throws BusinessException 工单不存在时抛出
     */
    public List<WorkOrderStatusHistory> history(Long id) {
        getById(id);
        return statusHistoryMapper.selectList(new LambdaQueryWrapper<WorkOrderStatusHistory>()
                .eq(WorkOrderStatusHistory::getOrderId, id)
                .orderByAsc(WorkOrderStatusHistory::getCreatedAt)
                .orderByAsc(WorkOrderStatusHistory::getId));
    }

    /**
     * 开工：工艺必须已发布 → 物料齐套校验 → 状态机流转到「生产中」→ 写入状态历史。
     *
     * @param id       工单主键
     * @param operator 操作人，取自登录令牌，为空时记为 system
     * @return 流转后的工单
     * @throws BusinessException 工单未绑定工艺、工艺未发布、物料不齐套或并发冲突时抛出
     */
    @Transactional
    public WorkOrder start(Long id, String operator) {
        WorkOrder order = getById(id);
        if (order.getProcessId() == null) {
            throw new BusinessException("工单未绑定工艺，无法开工");
        }
        Process process = processService.getById(order.getProcessId());
        if (!ApprovalStatus.PUBLISHED.name().equals(process.getApprovalStatus())) {
            throw new BusinessException("工艺未发布，无法开工");
        }
        KitCheckResult kit = kitCheckService.check(order);
        if (!kit.passed()) {
            throw new BusinessException("物料不齐套，缺料：" +
                    kit.shortages().stream().map(KitCheckResult.ShortageItem::matName)
                            .reduce((a, b) -> a + "、" + b).orElse(""));
        }
        transitionWithHistory(order, WorkOrderStatus.IN_PRODUCTION, operator, "开工");
        return order;
    }

    /**
     * 报工：幂等判重 → 校验报工数量与累计上限 → 写入报工记录 → 累加工单数量 → 转「待质检」→ 写入状态历史。
     *
     * <p>数量本身不合法（为空、非正数、合格数与不良数之和不等于报工数）返回 HTTP 400；
     * 累计报工超过工单数量属于业务拒绝，返回业务码 409。任一校验失败都不写报工记录。</p>
     *
     * <p>带幂等键的请求在同一工单内只生效一次：重复请求直接返回当前工单快照，
     * 不校验状态、不写报工记录、不累加数量、不写历史。并发同键请求由唯一索引兜底，
     * 后到者同样按重复请求处理。</p>
     *
     * @param id  工单主键
     * @param req 报工请求，包含数量、合格数、不良数、设备、操作人与幂等键
     * @return 流转后的工单；幂等重试时返回当前工单快照
     * @throws BadRequestException 报工数量非法或幂等键超长时抛出
     * @throws BusinessException   工单状态不允许、累计超限或并发冲突时抛出
     */
    @Transactional
    public WorkOrder report(Long id, ReportRequest req) {
        WorkOrder order = getById(id);
        String idempotencyKey = normalizeIdempotencyKey(req.idempotencyKey());
        if (idempotencyKey != null && reportExists(order.getId(), idempotencyKey)) {
            return order;
        }
        if (!WorkOrderStatus.IN_PRODUCTION.name().equals(order.getStatus())) {
            throw new BusinessException("仅「生产中」的工单可报工");
        }
        validateReportQty(order, req);

        WorkOrderReport report = new WorkOrderReport();
        report.setReportNo(codeGenerator.next("RP"));
        report.setIdempotencyKey(idempotencyKey);
        report.setOrderId(order.getId());
        report.setReportTime(LocalDateTime.now());
        report.setQty(req.qty());
        report.setDeviceId(req.deviceId());
        report.setOperator(req.operator());
        report.setQualifiedQty(req.qualifiedQty());
        report.setDefectQty(req.defectQty());
        try {
            workOrderReportMapper.insert(report);
        } catch (DuplicateKeyException e) {
            WorkOrder existing = duplicateReportSnapshot(order.getId(), idempotencyKey);
            if (existing != null) {
                return existing;
            }
            throw e;
        }

        order.setQtyDone(qtyDone(order) + req.qty());
        order.setQtyQualified(qtyValue(order.getQtyQualified()) + req.qualifiedQty());
        order.setQtyDefect(qtyValue(order.getQtyDefect()) + req.defectQty());
        transitionWithHistory(order, WorkOrderStatus.PENDING_QC, req.operator(),
                "报工 " + req.qty() + " 件");
        return order;
    }

    /**
     * 质检：先校验结论合法性 → 写入质检记录 → PASS 转「已完工」、FAIL 退回「生产中」→ 写入状态历史。
     *
     * <p>结论不是 PASS 或 FAIL 时在落库前拒绝，不产生质检记录。</p>
     *
     * @param id  工单主键
     * @param req 质检请求，包含结论、不良描述与质检员
     * @return 流转后的工单
     * @throws BusinessException 工单状态不允许、结论非法或并发冲突时抛出
     */
    @Transactional
    public WorkOrder qc(Long id, QcRequest req) {
        WorkOrder order = getById(id);
        if (!WorkOrderStatus.PENDING_QC.name().equals(order.getStatus())) {
            throw new BusinessException("仅「待质检」的工单可质检");
        }
        boolean pass = "PASS".equalsIgnoreCase(req.result());
        boolean fail = "FAIL".equalsIgnoreCase(req.result());
        if (!pass && !fail) {
            throw new BusinessException("质检结论必须为 PASS 或 FAIL");
        }

        QualityRecord record = new QualityRecord();
        record.setQualityNo(codeGenerator.next("QC"));
        record.setOrderId(order.getId());
        record.setInspector(req.inspector());
        record.setResult(req.result());
        record.setDefectDesc(req.defectDesc());
        record.setInspectTime(LocalDateTime.now());
        qualityRecordMapper.insert(record);

        String remark = StringUtils.hasText(req.defectDesc()) ? req.defectDesc() : "质检结论 " + req.result();
        if (pass) {
            transitionWithHistory(order, WorkOrderStatus.FINISHED, req.inspector(), remark);
            maybeFinishPlan(order.getPlanId());
        } else {
            transitionWithHistory(order, WorkOrderStatus.IN_PRODUCTION, req.inspector(), remark);
        }
        return order;
    }

    /**
     * 执行状态流转：状态机校验、乐观锁更新、同事务写入历史。
     *
     * @param order    待流转工单
     * @param target   目标状态
     * @param operator 操作人，为空时记为 system
     * @param remark   历史备注
     * @throws BusinessException 状态流转非法或乐观锁冲突时抛出
     */
    private void transitionWithHistory(WorkOrder order, WorkOrderStatus target, String operator, String remark) {
        String from = order.getStatus();
        stateMachine.transition(order, target);
        int updated = workOrderMapper.updateById(order);
        if (updated == 0) {
            throw new BusinessException(409, "工单已被其他操作更新，请刷新后重试");
        }
        WorkOrderStatusHistory history = new WorkOrderStatusHistory();
        history.setOrderId(order.getId());
        history.setFromStatus(from);
        history.setToStatus(target.name());
        history.setOperator(StringUtils.hasText(operator) ? operator : "system");
        history.setRemark(remark);
        history.setCreatedAt(LocalDateTime.now());
        statusHistoryMapper.insert(history);
    }

    /**
     * 校验报工数量：非空、正数、合格数与不良数之和等于报工数、累计不超过工单数量。
     *
     * @param order 当前工单，用于读取已报数量与工单数量
     * @param req   报工请求
     * @throws BadRequestException 数量字段缺失、非正数或合计不一致时抛出
     * @throws BusinessException   累计报工超过工单数量时抛出
     */
    private void validateReportQty(WorkOrder order, ReportRequest req) {
        if (req.qty() == null || req.qualifiedQty() == null || req.defectQty() == null) {
            throw new BadRequestException("报工数量、合格数量与不良数量均不能为空");
        }
        if (req.qty() <= 0) {
            throw new BadRequestException("报工数量必须大于 0");
        }
        if (req.qualifiedQty() < 0 || req.defectQty() < 0) {
            throw new BadRequestException("合格数量与不良数量不能为负数");
        }
        if (req.qualifiedQty() + req.defectQty() != req.qty()) {
            throw new BadRequestException("合格数量与不良数量之和必须等于报工数量");
        }
        int limit = qtyValue(order.getQty());
        if (qtyDone(order) + req.qty() > limit) {
            throw new BusinessException(409, "累计报工数量超过工单数量，剩余可报 " +
                    Math.max(0, limit - qtyDone(order)) + " 件");
        }
    }

    /**
     * 归一化幂等键：空白视为未提供，超过 64 字符按请求非法拒绝。
     *
     * @param raw 客户端传入的幂等键
     * @return 去除首尾空白后的幂等键，或 null
     * @throws BadRequestException 幂等键超过 64 字符时抛出
     */
    private String normalizeIdempotencyKey(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String key = raw.trim();
        if (key.length() > 64) {
            throw new BadRequestException("幂等键长度不能超过 64 个字符");
        }
        return key;
    }

    /**
     * 判断同一工单下该幂等键是否已有报工记录。
     *
     * @param orderId        工单主键
     * @param idempotencyKey 幂等键
     * @return 已存在返回 true
     */
    private boolean reportExists(Long orderId, String idempotencyKey) {
        Long count = workOrderReportMapper.selectCount(new LambdaQueryWrapper<WorkOrderReport>()
                .eq(WorkOrderReport::getOrderId, orderId)
                .eq(WorkOrderReport::getIdempotencyKey, idempotencyKey));
        return count != null && count > 0;
    }

    /**
     * 并发同键兜底：唯一索引冲突后用当前读确认键已存在，并返回最新工单快照。
     *
     * <p>MySQL 默认 REPEATABLE READ，普通查询沿用事务开始时的快照，看不到并发事务刚提交的
     * 报工记录；这里显式加 FOR UPDATE 走当前读，避免把可安全重试的请求误判为失败。</p>
     *
     * @param orderId        工单主键
     * @param idempotencyKey 幂等键
     * @return 键确已存在时返回最新工单快照，否则返回 null
     */
    private WorkOrder duplicateReportSnapshot(Long orderId, String idempotencyKey) {
        if (idempotencyKey == null) {
            return null;
        }
        List<WorkOrderReport> reports = workOrderReportMapper.selectList(new LambdaQueryWrapper<WorkOrderReport>()
                .eq(WorkOrderReport::getOrderId, orderId)
                .eq(WorkOrderReport::getIdempotencyKey, idempotencyKey)
                .last("FOR UPDATE"));
        if (reports.isEmpty()) {
            return null;
        }
        return workOrderMapper.selectOne(new LambdaQueryWrapper<WorkOrder>()
                .eq(WorkOrder::getId, orderId)
                .last("FOR UPDATE"));
    }

    /**
     * 读取累计报工数量，空值按 0 处理。
     *
     * @param order 工单
     * @return 累计报工数量
     */
    private int qtyDone(WorkOrder order) {
        return qtyValue(order.getQtyDone());
    }

    /**
     * 把可为空的数量字段归一化为 0。
     *
     * @param value 数量
     * @return 非空数量
     */
    private int qtyValue(Integer value) {
        return value == null ? 0 : value;
    }

    /** 计划下所有工单均已完工时，计划状态置为已完成。 */
    private void maybeFinishPlan(Long planId) {
        Long unfinished = workOrderMapper.selectCount(new LambdaQueryWrapper<WorkOrder>()
                .eq(WorkOrder::getPlanId, planId)
                .ne(WorkOrder::getStatus, WorkOrderStatus.FINISHED.name()));
        if (unfinished == null || unfinished == 0) {
            Plan plan = planMapper.selectById(planId);
            if (plan != null && !PlanStatus.FINISHED.name().equals(plan.getStatus())) {
                plan.setStatus(PlanStatus.FINISHED.name());
                planMapper.updateById(plan);
            }
        }
    }
}
