package com.zhizao.controller;

import com.zhizao.common.PageResult;
import com.zhizao.common.Result;
import com.zhizao.dto.QcRequest;
import com.zhizao.dto.ReportRequest;
import com.zhizao.entity.WorkOrder;
import com.zhizao.entity.WorkOrderStatusHistory;
import com.zhizao.service.WorkOrderService;
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
    @PostMapping("/{id}/start")
    public Result<WorkOrder> start(@PathVariable Long id) {
        return Result.ok(workOrderService.start(id, currentOperator()));
    }

    @PostMapping("/{id}/report")
    public Result<WorkOrder> report(@PathVariable Long id, @RequestBody ReportRequest request) {
        return Result.ok(workOrderService.report(id, request, currentOperator()));
    }

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
