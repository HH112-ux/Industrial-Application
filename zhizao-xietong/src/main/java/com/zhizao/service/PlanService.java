package com.zhizao.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
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

    private static final Logger log = LoggerFactory.getLogger(PlanService.class);

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

    /**
     * 新建计划：车间必填，不再回退到固定车间。
     *
     * @param plan 计划数据
     * @return 已保存的计划
     * @throws BusinessException 未指定车间时抛出
     */
    public Plan create(Plan plan) {
        plan.setId(null);
        plan.setPlanOrderNo(codeGenerator.next("PO"));
        plan.setStatus(PlanStatus.PENDING_SCHEDULE.name());
        plan.setWorkshop(requireWorkshop(plan.getWorkshop(), "计划"));
        planMapper.insert(plan);
        return plan;
    }

    /**
     * 更新计划：车间传入时必须非空，传 null 表示不修改。
     *
     * @param plan 计划数据
     * @return 更新后的计划
     * @throws BusinessException 车间传空时抛出
     */
    public Plan update(Plan plan) {
        getById(plan.getId());
        plan.setPlanOrderNo(null);
        plan.setStatus(null); // 状态走专用流转
        if (plan.getWorkshop() != null) {
            plan.setWorkshop(requireWorkshop(plan.getWorkshop(), "计划"));
        }
        planMapper.updateById(plan);
        return getById(plan.getId());
    }

    /**
     * 校验并规范化车间必填值。
     *
     * @param workshop 车间值
     * @param subject  业务对象名称，用于错误文案
     * @return 去除首尾空白后的车间
     * @throws BusinessException 车间为空时抛出
     */
    private static String requireWorkshop(String workshop, String subject) {
        if (!StringUtils.hasText(workshop)) {
            throw new BusinessException(subject + "必须指定车间");
        }
        return workshop.trim();
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
     * <p>一个计划只对应一张工单：先用条件更新把计划从「待排产」抢占为「进行中」，
     * 命中 0 行说明已被其他请求下达，直接返回 409 业务错误；抢占成功后才插入工单，
     * 插入失败随事务整体回滚，计划状态不会留下脏数据。
     * 工单上的 `dispatch_token` 唯一索引是最后一道兜底，命中时同样转成 409 业务错误，
     * 避免把重复下达暴露成 500。</p>
     *
     * <p>必须显式指定已发布工艺，工单会锁定该工艺版本；车间与设备可由请求覆盖，
     * 车间为空时取计划所属车间，设备为空时取计划上的所需设备。
     * 不再自动挑选「第一个已发布工艺」，避免多工艺产品下错工艺。</p>
     *
     * @param id      计划主键
     * @param request 下达请求，包含工艺版本、可选设备与车间
     * @return 新生成的工单
     * @throws BusinessException 计划状态不允许、工艺未发布、车间缺失或计划已下达工单时抛出
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
        String workshop = StringUtils.hasText(request.workshop()) ? request.workshop().trim() : plan.getWorkshop();
        String orderWorkshop = requireWorkshop(workshop, "工单");

        int claimed = planMapper.update(null, new LambdaUpdateWrapper<Plan>()
                .eq(Plan::getId, plan.getId())
                .eq(Plan::getStatus, PlanStatus.PENDING_SCHEDULE.name())
                .set(Plan::getStatus, PlanStatus.IN_PROGRESS.name()));
        if (claimed == 0) {
            throw new BusinessException(409, "该计划已下达工单，请勿重复操作");
        }

        WorkOrder order = new WorkOrder();
        order.setOrderNo(codeGenerator.next("WO"));
        order.setPlanId(plan.getId());
        order.setDispatchToken("PLAN-" + plan.getId());
        order.setProductCode(plan.getProductCode());
        order.setProductName(plan.getProductName());
        order.setQty(plan.getPlanQty());
        order.setBatch(plan.getBatch());
        order.setWorkshop(orderWorkshop);
        order.setDeviceId(request.deviceId() != null ? request.deviceId() : plan.getRequiredDeviceId());
        order.setProcessId(process.getId());
        order.setProcessVersion(process.getVersion());
        order.setStatus(WorkOrderStatus.PENDING.name());
        try {
            workOrderMapper.insert(order);
        } catch (DuplicateKeyException e) {
            log.warn("Work order insert rejected by unique key, planId={}, orderNo={}",
                    plan.getId(), order.getOrderNo(), e);
            throw new BusinessException(409, "该计划已下达工单，请勿重复操作");
        }

        return order;
    }
}
