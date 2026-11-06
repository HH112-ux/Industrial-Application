package com.zhizao.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhizao.common.BusinessException;
import com.zhizao.common.PageResult;
import com.zhizao.entity.Plan;
import com.zhizao.entity.Process;
import com.zhizao.entity.WorkOrder;
import com.zhizao.enums.ApprovalStatus;
import com.zhizao.enums.PlanStatus;
import com.zhizao.enums.WorkOrderStatus;
import com.zhizao.mapper.PlanMapper;
import com.zhizao.mapper.ProcessMapper;
import com.zhizao.mapper.WorkOrderMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 计划与调度服务。
 *
 * @author Caspian
 */
@Service
public class PlanService {

    private final PlanMapper planMapper;
    private final ProcessMapper processMapper;
    private final WorkOrderMapper workOrderMapper;
    private final CodeGenerator codeGenerator;

    public PlanService(PlanMapper planMapper, ProcessMapper processMapper, WorkOrderMapper workOrderMapper,
                       CodeGenerator codeGenerator) {
        this.planMapper = planMapper;
        this.processMapper = processMapper;
        this.workOrderMapper = workOrderMapper;
        this.codeGenerator = codeGenerator;
    }

    public PageResult<Plan> list(int pageNum, int pageSize, String keyword, String status) {
        Page<Plan> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<Plan> qw = new LambdaQueryWrapper<>();
        qw.and(StringUtils.hasText(keyword), w -> w
                .like(Plan::getPlanOrderNo, keyword)
                .or().like(Plan::getProductCode, keyword)
                .or().like(Plan::getProductName, keyword));
        qw.eq(StringUtils.hasText(status), Plan::getStatus, status);
        qw.orderByDesc(Plan::getId);
        planMapper.selectPage(page, qw);
        return PageResult.of(page.getTotal(), page.getRecords());
    }

    public Plan getById(Long id) {
        Plan plan = planMapper.selectById(id);
        if (plan == null) {
            throw new BusinessException("计划不存在");
        }
        return plan;
    }

    public Plan create(Plan plan) {
        plan.setId(null);
        plan.setPlanOrderNo(codeGenerator.next("PO"));
        plan.setStatus(PlanStatus.PENDING_SCHEDULE.name());
        planMapper.insert(plan);
        return plan;
    }

    public Plan update(Plan plan) {
        getById(plan.getId());
        plan.setPlanOrderNo(null);
        plan.setStatus(null); // 状态走专用流转
        planMapper.updateById(plan);
        return getById(plan.getId());
    }

    public void delete(Long id) {
        getById(id);
        planMapper.deleteById(id);
    }

    /**
     * 下达工单：待排产 → 进行中，并生成生产工单（自动匹配首个已发布工艺并锁定其版本）。
     *
     * @param id 计划主键
     * @return 新生成的工单
     */
    @Transactional
    public WorkOrder dispatch(Long id) {
        Plan plan = getById(id);
        if (!PlanStatus.PENDING_SCHEDULE.name().equals(plan.getStatus())) {
            throw new BusinessException("仅「待排产」的计划可下达工单");
        }
        Process process = processMapper.selectOne(new LambdaQueryWrapper<Process>()
                .eq(Process::getApprovalStatus, ApprovalStatus.PUBLISHED.name())
                .orderByAsc(Process::getId)
                .last("LIMIT 1"));

        WorkOrder order = new WorkOrder();
        order.setOrderNo(codeGenerator.next("WO"));
        order.setPlanId(plan.getId());
        order.setProductCode(plan.getProductCode());
        order.setProductName(plan.getProductName());
        order.setQty(plan.getPlanQty());
        order.setBatch(plan.getBatch());
        order.setWorkshop("一车间");
        order.setDeviceId(plan.getRequiredDeviceId());
        order.setProcessId(process != null ? process.getId() : null);
        order.setProcessVersion(process != null ? process.getVersion() : null);
        order.setStatus(WorkOrderStatus.PENDING.name());
        workOrderMapper.insert(order);

        plan.setStatus(PlanStatus.IN_PROGRESS.name());
        planMapper.updateById(plan);

        return order;
    }
}
