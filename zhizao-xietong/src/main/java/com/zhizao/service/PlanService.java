package com.zhizao.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhizao.common.BusinessException;
import com.zhizao.common.PageResult;
import com.zhizao.dto.DispatchRequest;
import com.zhizao.entity.Plan;
import com.zhizao.entity.Process;
import com.zhizao.entity.WorkOrder;
import com.zhizao.enums.ApprovalStatus;
import com.zhizao.enums.PlanStatus;
import com.zhizao.enums.WorkOrderStatus;
import com.zhizao.mapper.PlanMapper;
import com.zhizao.mapper.ProcessMapper;
import com.zhizao.mapper.WorkOrderMapper;
import com.zhizao.security.DataScope;
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
    private final DataScope dataScope;

    /**
     * 构造计划服务。
     *
     * @param planMapper      计划数据访问
     * @param processMapper   工艺数据访问
     * @param workOrderMapper 工单数据访问
     * @param codeGenerator   业务编号生成器
     * @param dataScope       车间数据域
     */
    public PlanService(PlanMapper planMapper, ProcessMapper processMapper, WorkOrderMapper workOrderMapper,
                       CodeGenerator codeGenerator, DataScope dataScope) {
        this.planMapper = planMapper;
        this.processMapper = processMapper;
        this.workOrderMapper = workOrderMapper;
        this.codeGenerator = codeGenerator;
        this.dataScope = dataScope;
    }

    /**
     * 分页查询计划。非管理员只能看到自己车间范围内的计划；管理端看板同样通过本查询取数。
     *
     * @param pageNum  页码
     * @param pageSize 每页条数
     * @param keyword  计划号、产品编号或名称关键字
     * @param status   计划状态
     * @return 计划分页结果
     */
    public PageResult<Plan> list(int pageNum, int pageSize, String keyword, String status) {
        Page<Plan> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<Plan> qw = new LambdaQueryWrapper<>();
        qw.and(StringUtils.hasText(keyword), w -> w
                .like(Plan::getPlanOrderNo, keyword)
                .or().like(Plan::getProductCode, keyword)
                .or().like(Plan::getProductName, keyword));
        qw.eq(StringUtils.hasText(status), Plan::getStatus, status);
        String workshop = dataScope.workshopFilter();
        qw.eq(workshop != null, Plan::getWorkshop, workshop);
        qw.orderByDesc(Plan::getId);
        planMapper.selectPage(page, qw);
        return PageResult.of(page.getTotal(), page.getRecords());
    }

    /**
     * 查询计划详情。
     *
     * @param id 计划主键
     * @return 计划详情
     * @throws BusinessException 计划不存在时抛出
     * @throws com.zhizao.common.ForbiddenException 计划不属于当前用户车间时抛出
     */
    public Plan getById(Long id) {
        Plan plan = planMapper.selectById(id);
        if (plan == null) {
            throw new BusinessException("计划不存在");
        }
        dataScope.requireWorkshop(plan.getWorkshop());
        return plan;
    }

    public Plan create(Plan plan) {
        plan.setId(null);
        plan.setPlanOrderNo(codeGenerator.next("PO"));
        plan.setStatus(PlanStatus.PENDING_SCHEDULE.name());
        if (!StringUtils.hasText(plan.getWorkshop())) {
            plan.setWorkshop("一车间");
        }
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

    /**
     * 删除计划：计划下仍挂有工单时拒绝删除，避免工单失去所属计划。
     *
     * @param id 计划主键
     * @throws BusinessException 计划下仍有工单时抛出
     */
    public void delete(Long id) {
        getById(id);
        Long mounted = workOrderMapper.selectCount(new LambdaQueryWrapper<WorkOrder>()
                .eq(WorkOrder::getPlanId, id));
        if (mounted != null && mounted > 0) {
            throw new BusinessException("计划下仍有 " + mounted + " 张工单，请先处理工单后再删除");
        }
        planMapper.deleteById(id);
    }

    /**
     * 下达工单：待排产 → 进行中，并生成生产工单。
     *
     * <p>必须显式指定已发布工艺，工单会锁定该工艺版本；车间与设备可由请求覆盖，
     * 设备为空时取计划上的所需设备。不再自动挑选「第一个已发布工艺」，避免多工艺产品下错工艺。</p>
     *
     * @param id      计划主键
     * @param request 下达请求，包含工艺版本、可选设备与车间
     * @return 新生成的工单
     * @throws BusinessException 计划状态不允许或工艺未发布时抛出
     */
    @Transactional
    public WorkOrder dispatch(Long id, DispatchRequest request) {
        Plan plan = getById(id);
        if (!PlanStatus.PENDING_SCHEDULE.name().equals(plan.getStatus())) {
            throw new BusinessException("仅「待排产」的计划可下达工单");
        }
        Process process = request == null || request.processId() == null
                ? null : processMapper.selectById(request.processId());
        if (process == null || !ApprovalStatus.PUBLISHED.name().equals(process.getApprovalStatus())) {
            throw new BusinessException("下达工单必须指定已发布工艺");
        }

        WorkOrder order = new WorkOrder();
        order.setOrderNo(codeGenerator.next("WO"));
        order.setPlanId(plan.getId());
        order.setProductCode(plan.getProductCode());
        order.setProductName(plan.getProductName());
        order.setQty(plan.getPlanQty());
        order.setBatch(plan.getBatch());
        order.setWorkshop(StringUtils.hasText(request.workshop()) ? request.workshop().trim() : "一车间");
        order.setDeviceId(request.deviceId() != null ? request.deviceId() : plan.getRequiredDeviceId());
        order.setProcessId(process.getId());
        order.setProcessVersion(process.getVersion());
        order.setStatus(WorkOrderStatus.PENDING.name());
        workOrderMapper.insert(order);

        plan.setStatus(PlanStatus.IN_PROGRESS.name());
        planMapper.updateById(plan);

        return order;
    }
}
