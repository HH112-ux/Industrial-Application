package com.zhizao.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhizao.common.BusinessException;
import com.zhizao.common.PageResult;
import com.zhizao.dto.QcRequest;
import com.zhizao.dto.ReportRequest;
import com.zhizao.entity.Plan;
import com.zhizao.entity.Process;
import com.zhizao.entity.QualityRecord;
import com.zhizao.entity.WorkOrder;
import com.zhizao.entity.WorkOrderReport;
import com.zhizao.enums.ApprovalStatus;
import com.zhizao.enums.PlanStatus;
import com.zhizao.enums.WorkOrderStatus;
import com.zhizao.mapper.PlanMapper;
import com.zhizao.mapper.QualityRecordMapper;
import com.zhizao.mapper.WorkOrderMapper;
import com.zhizao.mapper.WorkOrderReportMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
public class WorkOrderService {

    private final WorkOrderMapper workOrderMapper;
    private final WorkOrderReportMapper workOrderReportMapper;
    private final QualityRecordMapper qualityRecordMapper;
    private final PlanMapper planMapper;
    private final WorkOrderStateMachine stateMachine;
    private final KitCheckService kitCheckService;
    private final ProcessService processService;
    private final CodeGenerator codeGenerator;

    public WorkOrderService(WorkOrderMapper workOrderMapper, WorkOrderReportMapper workOrderReportMapper,
                            QualityRecordMapper qualityRecordMapper, PlanMapper planMapper,
                            WorkOrderStateMachine stateMachine, KitCheckService kitCheckService,
                            ProcessService processService, CodeGenerator codeGenerator) {
        this.workOrderMapper = workOrderMapper;
        this.workOrderReportMapper = workOrderReportMapper;
        this.qualityRecordMapper = qualityRecordMapper;
        this.planMapper = planMapper;
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
     * 开工：工艺必须已发布 → 物料齐套校验 → 状态机流转到「生产中」。
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
        stateMachine.transition(order, WorkOrderStatus.IN_PRODUCTION);
        workOrderMapper.updateById(order);
        return order;
    }

    /**
     * 报工：生产中 → 待质检，记录报工数据。
     */
    @Transactional
    public WorkOrder report(Long id, ReportRequest req) {
        WorkOrder order = getById(id);
        if (!WorkOrderStatus.IN_PRODUCTION.name().equals(order.getStatus())) {
            throw new BusinessException("仅「生产中」的工单可报工");
        }
        WorkOrderReport report = new WorkOrderReport();
        report.setReportNo(codeGenerator.next("RP"));
        report.setOrderId(order.getId());
        report.setReportTime(LocalDateTime.now());
        report.setQty(req.qty());
        report.setDeviceId(req.deviceId());
        report.setOperator(req.operator());
        report.setQualifiedQty(req.qualifiedQty());
        report.setDefectQty(req.defectQty());
        workOrderReportMapper.insert(report);

        stateMachine.transition(order, WorkOrderStatus.PENDING_QC);
        workOrderMapper.updateById(order);
        return order;
    }

    /**
     * 质检：待质检 → 已完工（PASS）或退回生产中（FAIL），记录质检数据。
     */
    @Transactional
    public WorkOrder qc(Long id, QcRequest req) {
        WorkOrder order = getById(id);
        if (!WorkOrderStatus.PENDING_QC.name().equals(order.getStatus())) {
            throw new BusinessException("仅「待质检」的工单可质检");
        }
        QualityRecord record = new QualityRecord();
        record.setQualityNo(codeGenerator.next("QC"));
        record.setOrderId(order.getId());
        record.setInspector(req.inspector());
        record.setResult(req.result());
        record.setDefectDesc(req.defectDesc());
        record.setInspectTime(LocalDateTime.now());
        qualityRecordMapper.insert(record);

        if ("PASS".equalsIgnoreCase(req.result())) {
            stateMachine.transition(order, WorkOrderStatus.FINISHED);
            workOrderMapper.updateById(order);
            maybeFinishPlan(order.getPlanId());
        } else if ("FAIL".equalsIgnoreCase(req.result())) {
            stateMachine.transition(order, WorkOrderStatus.IN_PRODUCTION);
            workOrderMapper.updateById(order);
        } else {
            throw new BusinessException("质检结论必须为 PASS 或 FAIL");
        }
        return order;
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
