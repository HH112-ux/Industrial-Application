package com.zhizao.controller;

import com.zhizao.common.PageResult;
import com.zhizao.common.Result;
import com.zhizao.dto.QcRequest;
import com.zhizao.dto.ReportRequest;
import com.zhizao.dto.WorkOrderStatusCount;
import com.zhizao.entity.WorkOrder;
import com.zhizao.entity.WorkOrderStatusHistory;
import com.zhizao.service.WorkOrderService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;

/**
 * 生产执行接口：工单开工、报工、质检与状态流转历史查询。
 *
 * @author Caspian
 */
@Tag(name = "生产执行")
@RestController
@RequestMapping("/api/work-orders")
public class WorkOrderController {

    private final WorkOrderService workOrderService;

    public WorkOrderController(WorkOrderService workOrderService) {
        this.workOrderService = workOrderService;
    }

    @GetMapping
    public Result<PageResult<WorkOrder>> list(@RequestParam(defaultValue = "1") int pageNum,
                                              @RequestParam(defaultValue = "10") int pageSize,
                                              @RequestParam(required = false) String keyword,
                                              @RequestParam(required = false) String status) {
        return Result.ok(workOrderService.list(pageNum, pageSize, keyword, status));
    }

    /**
     * 按状态聚合工单数量，供看板饼图使用。
     *
     * @return 各状态工单数量
     */
    @GetMapping("/stats/status")
    public Result<List<WorkOrderStatusCount>> statusCounts() {
        return Result.ok(workOrderService.countByStatus());
    }

    @GetMapping("/{id}")
    public Result<WorkOrder> get(@PathVariable Long id) {
        return Result.ok(workOrderService.getById(id));
    }

    /**
     * 开工。操作人取自登录令牌，不再由查询参数传入，避免伪造操作人。
     *
     * @param id 工单主键
     * @return 流转后的工单；并发冲突时返回 HTTP 200 + 业务码 409
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'USER', 'WORKER')")
    @PostMapping("/{id}/start")
    public Result<WorkOrder> start(@PathVariable Long id) {
        return Result.ok(workOrderService.start(id, currentOperator()));
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'USER', 'WORKER')")
    @PostMapping("/{id}/report")
    public Result<WorkOrder> report(@PathVariable Long id, @RequestBody ReportRequest request) {
        return Result.ok(workOrderService.report(id, request, currentOperator()));
    }

    /**
     * 质检是判定动作，只有管理员与普通用户（质检员）可以执行，一线员工不参与判定。
     *
     * @param id      工单主键
     * @param request 质检结论与不良描述
     * @return 流转后的工单
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'USER')")
    @PostMapping("/{id}/qc")
    public Result<WorkOrder> qc(@PathVariable Long id, @RequestBody QcRequest request) {
        return Result.ok(workOrderService.qc(id, request, currentOperator()));
    }

    /**
     * 查询工单状态流转历史，按发生时间升序返回。
     *
     * @param id 工单主键
     * @return 历史记录列表，包含前后状态、操作人、备注与发生时间
     */
    @GetMapping("/{id}/history")
    public Result<List<WorkOrderStatusHistory>> history(@PathVariable Long id) {
        return Result.ok(workOrderService.history(id));
    }

    /**
     * 读取当前登录用户名，未认证时缺省为 system。
     *
     * @return 用于留痕的操作人
     */
    private String currentOperator() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && StringUtils.hasText(auth.getName()) ? auth.getName() : "system";
    }
}
