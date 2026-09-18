package com.zhizao.controller;

import com.zhizao.common.PageResult;
import com.zhizao.common.Result;
import com.zhizao.entity.Plan;
import com.zhizao.entity.WorkOrder;
import com.zhizao.service.PlanService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "计划与调度")
@RestController
@RequestMapping("/api/plans")
public class PlanController {

    private final PlanService planService;

    public PlanController(PlanService planService) {
        this.planService = planService;
    }

    @GetMapping
    public Result<PageResult<Plan>> list(@RequestParam(defaultValue = "1") int pageNum,
                                         @RequestParam(defaultValue = "10") int pageSize,
                                         @RequestParam(required = false) String keyword,
                                         @RequestParam(required = false) String status) {
        return Result.ok(planService.list(pageNum, pageSize, keyword, status));
    }

    @GetMapping("/{id}")
    public Result<Plan> get(@PathVariable Long id) {
        return Result.ok(planService.getById(id));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public Result<Plan> create(@RequestBody Plan plan) {
        return Result.ok(planService.create(plan));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public Result<Plan> update(@PathVariable Long id, @RequestBody Plan plan) {
        plan.setId(id);
        return Result.ok(planService.update(plan));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        planService.delete(id);
        return Result.ok();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{id}/dispatch")
    public Result<WorkOrder> dispatch(@PathVariable Long id) {
        return Result.ok(planService.dispatch(id));
    }
}
