package com.zhizao.controller;

import com.zhizao.common.PageResult;
import com.zhizao.common.Result;
import com.zhizao.dto.QcRequest;
import com.zhizao.dto.ReportRequest;
import com.zhizao.entity.WorkOrder;
import com.zhizao.service.WorkOrderService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.tags.Tag;

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

    @PostMapping("/{id}/start")
    public Result<WorkOrder> start(@PathVariable Long id,
                                   @RequestParam(defaultValue = "system") String operator) {
        return Result.ok(workOrderService.start(id, operator));
    }

    @PostMapping("/{id}/report")
    public Result<WorkOrder> report(@PathVariable Long id, @RequestBody ReportRequest request) {
        return Result.ok(workOrderService.report(id, request));
    }

    @PostMapping("/{id}/qc")
    public Result<WorkOrder> qc(@PathVariable Long id, @RequestBody QcRequest request) {
        return Result.ok(workOrderService.qc(id, request));
    }
}
