package com.zhizao.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhizao.common.BadRequestException;
import com.zhizao.common.BusinessException;
import com.zhizao.common.BusinessMetrics;
import com.zhizao.common.PageResult;
import com.zhizao.dto.QcRequest;
import com.zhizao.dto.ReportRequest;
import com.zhizao.dto.WorkOrderStatusCount;
import com.zhizao.entity.Device;
import com.zhizao.entity.Plan;
import com.zhizao.entity.Process;
import com.zhizao.entity.QualityRecord;
import com.zhizao.entity.ScheduleEntry;
import com.zhizao.entity.WorkOrder;
import com.zhizao.entity.WorkOrderReport;
import com.zhizao.entity.WorkOrderStatusHistory;
import com.zhizao.enums.ApprovalStatus;
import com.zhizao.enums.PlanStatus;
import com.zhizao.enums.ScheduleStatus;
import com.zhizao.enums.WorkOrderStatus;
import com.zhizao.mapper.DeviceMapper;
import com.zhizao.mapper.PlanMapper;
import com.zhizao.mapper.QualityRecordMapper;
import com.zhizao.mapper.ScheduleEntryMapper;
import com.zhizao.mapper.WorkOrderMapper;
import com.zhizao.mapper.WorkOrderReportMapper;
import com.zhizao.mapper.WorkOrderStatusHistoryMapper;
import com.zhizao.security.DataScope;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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
    private final ScheduleEntryMapper scheduleEntryMapper;
    private final DeviceMapper deviceMapper;
    private final WorkOrderStateMachine stateMachine;
    private final KitCheckService kitCheckService;
    private final ProcessService processService;
    private final CodeGenerator codeGenerator;
    private final DataScope dataScope;
    private final BusinessMetrics businessMetrics;
    private final FinishedGoodsInboundService finishedGoodsInboundService;

    /**
     * 构造工单服务。
     *
     * @param workOrderMapper      工单数据访问
     * @param workOrderReportMapper 报工数据访问
     * @param qualityRecordMapper  质检数据访问
     * @param planMapper           计划数据访问
     * @param statusHistoryMapper  状态流转历史数据访问
     * @param scheduleEntryMapper  排程结果数据访问
     * @param deviceMapper         设备数据访问
     * @param stateMachine         工单状态机
     * @param kitCheckService      齐套校验服务
     * @param processService       工艺服务
     * @param codeGenerator        业务编号生成器
     * @param dataScope            车间数据域
     * @param businessMetrics      业务指标
     * @param finishedGoodsInboundService 成品入库服务，完工后生成待入库单
     */
    public WorkOrderService(WorkOrderMapper workOrderMapper, WorkOrderReportMapper workOrderReportMapper,
                            QualityRecordMapper qualityRecordMapper, PlanMapper planMapper,
                            WorkOrderStatusHistoryMapper statusHistoryMapper,
                            ScheduleEntryMapper scheduleEntryMapper, DeviceMapper deviceMapper,
                            WorkOrderStateMachine stateMachine,
                            KitCheckService kitCheckService, ProcessService processService,
                            CodeGenerator codeGenerator, DataScope dataScope, BusinessMetrics businessMetrics,
                            FinishedGoodsInboundService finishedGoodsInboundService) {
        this.workOrderMapper = workOrderMapper;
        this.workOrderReportMapper = workOrderReportMapper;
        this.qualityRecordMapper = qualityRecordMapper;
        this.planMapper = planMapper;
        this.statusHistoryMapper = statusHistoryMapper;
        this.scheduleEntryMapper = scheduleEntryMapper;
        this.deviceMapper = deviceMapper;
        this.stateMachine = stateMachine;
        this.kitCheckService = kitCheckService;
        this.processService = processService;
        this.codeGenerator = codeGenerator;
        this.dataScope = dataScope;
        this.businessMetrics = businessMetrics;
        this.finishedGoodsInboundService = finishedGoodsInboundService;
    }

    /**
     * 分页查询工单。非管理员只能看到自己车间范围内的工单；管理端看板同样通过本查询取数。
     *
     * @param pageNum  页码
     * @param pageSize 每页条数
     * @param keyword  工单号、产品编号、名称或车间关键字
     * @param status   工单状态
     * @return 工单分页结果
     */
    public PageResult<WorkOrder> list(int pageNum, int pageSize, String keyword, String status) {
        Page<WorkOrder> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<WorkOrder> qw = new LambdaQueryWrapper<>();
        qw.and(StringUtils.hasText(keyword), w -> w
                .like(WorkOrder::getOrderNo, keyword)
                .or().like(WorkOrder::getProductCode, keyword)
                .or().like(WorkOrder::getProductName, keyword)
                .or().like(WorkOrder::getWorkshop, keyword));
        qw.eq(StringUtils.hasText(status), WorkOrder::getStatus, status);
        String workshop = dataScope.workshopFilter();
        qw.eq(workshop != null, WorkOrder::getWorkshop, workshop);
        qw.orderByDesc(WorkOrder::getId);
        workOrderMapper.selectPage(page, qw);
        return PageResult.of(page.getTotal(), page.getRecords());
    }

    /**
     * 按状态聚合工单数量，供看板饼图使用；非管理员只统计自己车间范围。
     *
     * @return 各状态工单数量，未出现的状态不会返回
     */
    public List<WorkOrderStatusCount> countByStatus() {
        QueryWrapper<WorkOrder> qw = new QueryWrapper<>();
        qw.select("status", "COUNT(*) AS cnt");
        String workshop = dataScope.workshopFilter();
        qw.eq(workshop != null, "workshop", workshop);
        qw.groupBy("status");
        List<Map<String, Object>> rows = workOrderMapper.selectMaps(qw);
        return rows.stream()
                .map(row -> new WorkOrderStatusCount(String.valueOf(row.get("status")),
                        ((Number) row.get("cnt")).longValue()))
                .toList();
    }

    /**
     * 查询工单详情；开工、报工、质检与历史查询都经过这里，跨车间访问统一在入口拦截。
     *
     * @param id 工单主键
     * @return 工单详情
     * @throws BusinessException 工单不存在时抛出
     * @throws com.zhizao.common.ForbiddenException 工单不属于当前用户车间时抛出
     */
    public WorkOrder getById(Long id) {
        WorkOrder order = workOrderMapper.selectById(id);
        if (order == null) {
            throw new BusinessException("工单不存在");
        }
        dataScope.requireWorkshop(order.getWorkshop());
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
     * 开工：工艺已发布 → 排程记录存在 → 物料齐套校验并预占库存 → 状态机流转到「生产中」→ 写入状态历史。
     *
     * <p>开工前必须有排程记录，避免绕过排程直接占用设备；若排程窗口早于当前时间，
     * 按实际开工时间整体顺延，使后续排产能看到这张工单对设备的占用。</p>
     *
     * <p>齐套按「库存 − 其他工单已预占」核算，校验通过后在同一事务内写入预占记录；
     * 后续状态流转失败会整体回滚，不会留下悬空预占。</p>
     *
     * @param id       工单主键
     * @param operator 操作人，取自登录令牌，为空时记为 system
     * @return 流转后的工单
     * @throws BusinessException 工单未绑定工艺、工艺未发布、未排程、物料不齐套或并发冲突时抛出
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
        List<ScheduleEntry> schedule = scheduleEntryMapper.selectList(new LambdaQueryWrapper<ScheduleEntry>()
                .eq(ScheduleEntry::getWorkOrderId, order.getId())
                .eq(ScheduleEntry::getStatus, ScheduleStatus.SCHEDULED.name())
                .orderByAsc(ScheduleEntry::getOperationSeq)
                .orderByAsc(ScheduleEntry::getId));
        if (schedule.isEmpty()) {
            throw new BusinessException("工单尚未排程，请先运行排产后再开工");
        }
        KitCheckResult kit = reserveSafely(order);
        if (!kit.passed()) {
            if (kit.error() != null) {
                throw new BusinessException(kit.error());
            }
            throw new BusinessException("物料不齐套，缺料：" +
                    kit.shortages().stream().map(KitCheckResult.ShortageItem::matName)
                            .reduce((a, b) -> a + "、" + b).orElse(""));
        }
        transitionWithHistory(order, WorkOrderStatus.IN_PRODUCTION, operator, "开工");
        alignActualOccupation(schedule);
        return order;
    }

    /**
     * 执行开工预占；同一工单并发开工时唯一键 {@code uk_work_order_mat} 冲突按并发冲突处理。
     *
     * <p>预占记录以 {@code (work_order_id, mat_code)} 唯一，两个请求同时通过齐套校验时会有一个
     * 插入失败。该失败与乐观锁冲突语义一致——工单已被其他请求推进，返回业务码 409 让客户端刷新，
     * 不能落到全局兜底变成 500。</p>
     *
     * @param order 工单
     * @return 齐套结果
     * @throws BusinessException 唯一键冲突时抛出，业务码 409
     */
    private KitCheckResult reserveSafely(WorkOrder order) {
        try {
            return kitCheckService.reserve(order);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(409, "工单已被其他操作更新，请刷新后重试");
        }
    }

    /**
     * 把早于当前时间的排程窗口整体顺延到实际开工时间，保持工序先后与时长不变。
     *
     * <p>排程结果只保留时间窗，不保留实际进度；开工时以「现在」作为第一道工序的实际开始，
     * 让后续排产把这张在产工单继续视为设备占用，而不是把其他工单排到同一时段。</p>
     *
     * @param entries 工单已排程的工序记录
     */
    private void alignActualOccupation(List<ScheduleEntry> entries) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime firstStart = entries.stream().map(ScheduleEntry::getStartTime)
                .filter(Objects::nonNull).min(Comparator.naturalOrder()).orElse(null);
        if (firstStart == null || !firstStart.isBefore(now)) {
            return;
        }
        Duration shift = Duration.between(firstStart, now);
        for (ScheduleEntry entry : entries) {
            if (entry.getStartTime() != null) {
                entry.setStartTime(entry.getStartTime().plus(shift));
            }
            if (entry.getEndTime() != null) {
                entry.setEndTime(entry.getEndTime().plus(shift));
            }
            String message = StringUtils.hasText(entry.getMessage()) ? entry.getMessage() + "；" : "";
            entry.setMessage(message + "按实际开工时间顺延至当前时间");
            scheduleEntryMapper.updateById(entry);
        }
    }

    /**
     * 报工：幂等判重 → 校验报工数量与累计上限 → 写入报工记录 → 累加工单数量 →
     * （报满时）转「待质检」→ 写入状态历史。
     *
     * <p>支持多次累计：每次报工都留在「生产中」并累加数量，只有剩余可报数量为 0
     * （累计达到「工单数量 + 可返工额度」）时才在同一事务内转「待质检」。
     * 报满前不可送检，质检不可跳过，PASS 才完工。</p>
     *
     * <p>数量本身不合法（为空、非正数、合格数与不良数之和不等于报工数）返回 HTTP 400；
     * 累计报工超过「工单数量 + 可返工额度」属于业务拒绝，返回业务码 409。任一校验失败都不写报工记录。</p>
     *
     * <p>带幂等键的请求在同一工单内只生效一次：重复请求直接返回当前工单快照，
     * 不校验状态、不写报工记录、不累加数量、不写历史。并发同键请求由唯一索引兜底，
     * 后到者同样按重复请求处理。</p>
     *
     * <p>并发报工由乐观锁裁决：两个请求同时读到同一版本时，只有一个能更新成功；
     * 另一个命中 0 行并按业务码 409 返回，其报工记录与历史随事务回滚，
     * 因此不会出现重复累计或两次「待质检」转移。</p>
     *
     * @param id       工单主键
     * @param req      报工请求，包含数量、合格数、不良数、设备与幂等键
     * @param operator 操作人，取自登录令牌
     * @return 累加或流转后的工单；幂等重试时返回当前工单快照
     * @throws BadRequestException 报工数量非法或幂等键超长时抛出
     * @throws BusinessException   工单状态不允许、累计超限或并发冲突时抛出
     */
    @Transactional
    public WorkOrder report(Long id, ReportRequest req, String operator) {
        WorkOrder order = getById(id);
        String idempotencyKey = normalizeIdempotencyKey(req.idempotencyKey());
        if (idempotencyKey != null && reportExists(order.getId(), idempotencyKey)) {
            return order;
        }
        if (!WorkOrderStatus.IN_PRODUCTION.name().equals(order.getStatus())) {
            throw new BusinessException("仅「生产中」的工单可报工");
        }
        validateReportQty(order, req);
        validateDevice(order, req.deviceId());
        long limit = (long) qtyValue(order.getQty()) + qtyValue(order.getQtyRework());
        long nextDone = (long) qtyDone(order) + req.qty();

        WorkOrderReport report = new WorkOrderReport();
        report.setReportNo(codeGenerator.next("RP"));
        report.setIdempotencyKey(idempotencyKey);
        report.setOrderId(order.getId());
        report.setReportTime(LocalDateTime.now());
        report.setQty(req.qty());
        report.setDeviceId(req.deviceId());
        report.setOperator(operator);
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

        order.setQtyDone((int) nextDone);
        order.setQtyQualified(qtyValue(order.getQtyQualified()) + req.qualifiedQty());
        order.setQtyDefect(qtyValue(order.getQtyDefect()) + req.defectQty());
        String remark = "报工 " + req.qty() + " 件（累计 " + nextDone + "/" + limit + "）";
        if (nextDone == limit) {
            transitionWithHistory(order, WorkOrderStatus.PENDING_QC, operator,
                    remark + "，报满转待质检");
        } else {
            accumulateWithHistory(order, operator, remark);
        }
        businessMetrics.workOrderReported();
        return order;
    }

    /**
     * 质检：先校验结论合法性 → 写入质检记录 → PASS 转「已完工」、FAIL 退回「生产中」→ 写入状态历史。
     *
     * <p>FAIL 时按本轮全部有效报工数量释放可返工额度，使被退回的批次可以重新报工，
     * 避免满额报工后质检失败导致工单永久卡在「生产中」。本轮指上一次质检之后、
     * 当前生产阶段内的全部报工；释放后剩余可报数量回到计划数量，不会超过计划数。</p>
     *
     * <p>{@code qtyQualified} 是「当前生产轮次的合格数」：报工时按轮次累加，
     * FAIL 时整轮退回并清零，因此 PASS 后它就是质检通过的合格数，供成品入库使用。
     * 这样返工重报不会把上一轮的不良批次重复计入合格数。</p>
     *
     * <p>结论不是 PASS 或 FAIL 时在落库前拒绝，不产生质检记录。</p>
     *
     * @param id       工单主键
     * @param req      质检请求，包含结论与不良描述
     * @param operator 质检员，取自登录令牌
     * @return 流转后的工单
     * @throws BusinessException 工单状态不允许、结论非法或并发冲突时抛出
     */
    @Transactional
    public WorkOrder qc(Long id, QcRequest req, String operator) {
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
        record.setInspector(operator);
        record.setResult(req.result());
        record.setDefectDesc(req.defectDesc());
        record.setInspectTime(LocalDateTime.now());
        qualityRecordMapper.insert(record);

        String remark = StringUtils.hasText(req.defectDesc()) ? req.defectDesc() : "质检结论 " + req.result();
        if (pass) {
            transitionWithHistory(order, WorkOrderStatus.FINISHED, operator, remark);
            kitCheckService.consume(order);
            maybeFinishPlan(order.getPlanId());
            finishedGoodsInboundService.generateFor(order);
        } else {
            long rework = (long) qtyValue(order.getQtyRework()) + unreleasedReportQty(order);
            if (rework > Integer.MAX_VALUE) {
                throw new BusinessException("可返工额度超出系统上限");
            }
            order.setQtyRework((int) rework);
            // 整轮退回：清零本轮合格数，返工重报后从 0 重新累计，避免重复计入成品入库
            order.setQtyQualified(0);
            transitionWithHistory(order, WorkOrderStatus.IN_PRODUCTION, operator, remark);
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
        insertHistory(order.getId(), from, target.name(), operator, remark);
    }

    /**
     * 部分报工：状态不变，仅累加数量并写一条历史。
     *
     * <p>与状态流转共用乐观锁路径：更新命中 0 行说明工单已被并发请求改动，
     * 按业务码 409 返回并回滚本次报工与历史，避免并发请求重复累计。</p>
     *
     * @param order    待累加工单
     * @param operator 操作人，为空时记为 system
     * @param remark   历史备注
     * @throws BusinessException 乐观锁冲突时抛出
     */
    private void accumulateWithHistory(WorkOrder order, String operator, String remark) {
        order.setUpdatedAt(LocalDateTime.now());
        int updated = workOrderMapper.updateById(order);
        if (updated == 0) {
            throw new BusinessException(409, "工单已被其他操作更新，请刷新后重试");
        }
        insertHistory(order.getId(), order.getStatus(), order.getStatus(), operator, remark);
    }

    /**
     * 写入一条状态流转历史，只追加不修改。
     *
     * @param orderId   工单主键
     * @param from      流转前状态
     * @param to        流转后状态
     * @param operator  操作人，为空时记为 system
     * @param remark    备注
     */
    private void insertHistory(Long orderId, String from, String to, String operator, String remark) {
        WorkOrderStatusHistory history = new WorkOrderStatusHistory();
        history.setOrderId(orderId);
        history.setFromStatus(from);
        history.setToStatus(to);
        history.setOperator(StringUtils.hasText(operator) ? operator : "system");
        history.setRemark(remark);
        history.setCreatedAt(LocalDateTime.now());
        statusHistoryMapper.insert(history);
    }

    /**
     * 校验报工数量：非空、正数、合格数与不良数之和等于报工数、
     * 累计不超过「工单数量 + 可返工额度」。
     *
     * @param order 当前工单，用于读取已报数量、工单数量与可返工额度
     * @param req   报工请求
     * @throws BadRequestException 数量字段缺失、非正数或合计不一致时抛出
     * @throws BusinessException   累计报工超过上限时抛出
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
        // 用 long 相加，防止两个接近 Integer.MAX_VALUE 的数量相加溢出后绕过合计校验。
        if ((long) req.qualifiedQty() + req.defectQty() != req.qty().longValue()) {
            throw new BadRequestException("合格数量与不良数量之和必须等于报工数量");
        }
        long limit = (long) qtyValue(order.getQty()) + qtyValue(order.getQtyRework());
        long done = qtyDone(order);
        if (done + req.qty() > limit) {
            throw new BusinessException(409, "累计报工数量超过工单数量与可返工额度，剩余可报 " +
                    Math.max(0, limit - done) + " 件");
        }
        if (done + req.qty() > Integer.MAX_VALUE) {
            throw new BusinessException("累计报工数量超出系统上限");
        }
    }

    /**
     * 校验报工设备归属：设备必须存在且属于工单所在车间，避免把报工挂到其他车间的设备上。
     *
     * @param order    工单
     * @param deviceId 报工设备主键，可为空
     * @throws BusinessException 设备不存在或不属于工单车间时抛出
     */
    private void validateDevice(WorkOrder order, Long deviceId) {
        if (deviceId == null) {
            return;
        }
        Device device = deviceMapper.selectById(deviceId);
        if (device == null) {
            throw new BusinessException("报工设备不存在");
        }
        if (!Objects.equals(order.getWorkshop(), device.getWorkshop())) {
            throw new BusinessException("报工设备不属于工单车间，无法关联");
        }
    }

    /**
     * 计算本轮尚未释放的报工数量，用于质检失败时转为可返工额度。
     *
     * <p>「本轮」指上一次质检（PASS 或 FAIL）之后、当前生产阶段内的全部有效报工。
     * 每次报工增加 {@code qtyDone}，每次质检 FAIL 把本轮数量加入 {@code qtyRework}，
     * 因此两者之差始终等于本轮尚未释放的报工数：单次报工时与报工数量相等，
     * 多次累计时等于本轮各次报工之和，同一批报工释放后差值归零，不会被重复释放。</p>
     *
     * @param order 当前「待质检」工单
     * @return 本轮尚未释放的报工数量
     */
    private int unreleasedReportQty(WorkOrder order) {
        return qtyDone(order) - qtyValue(order.getQtyRework());
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
