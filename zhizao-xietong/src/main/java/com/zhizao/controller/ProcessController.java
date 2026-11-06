package com.zhizao.controller;

import com.zhizao.common.PageResult;
import com.zhizao.common.Result;
import com.zhizao.entity.Process;
import com.zhizao.entity.ProcessOperation;
import com.zhizao.service.ProcessOperationService;
import com.zhizao.service.ProcessService;
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

import java.util.List;

/**
 * 工艺管理接口：工艺基础信息、版本复制、审批流转与工序维护。
 *
 * @author Caspian
 */
@Tag(name = "工艺管理")
@RestController
@RequestMapping("/api/processes")
public class ProcessController {

    private final ProcessService processService;
    private final ProcessOperationService processOperationService;

    public ProcessController(ProcessService processService, ProcessOperationService processOperationService) {
        this.processService = processService;
        this.processOperationService = processOperationService;
    }

    @GetMapping
    public Result<PageResult<Process>> list(@RequestParam(defaultValue = "1") int pageNum,
                                            @RequestParam(defaultValue = "10") int pageSize,
                                            @RequestParam(required = false) String keyword,
                                            @RequestParam(required = false) String approvalStatus) {
        return Result.ok(processService.list(pageNum, pageSize, keyword, approvalStatus));
    }

    @GetMapping("/{id}")
    public Result<Process> get(@PathVariable Long id) {
        return Result.ok(processService.getById(id));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public Result<Process> create(@RequestBody Process process) {
        return Result.ok(processService.create(process));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public Result<Process> update(@PathVariable Long id, @RequestBody Process process) {
        process.setId(id);
        return Result.ok(processService.update(process));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        processService.delete(id);
        return Result.ok();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{id}/submit")
    public Result<Process> submit(@PathVariable Long id) {
        return Result.ok(processService.submit(id));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{id}/approve")
    public Result<Process> approve(@PathVariable Long id) {
        return Result.ok(processService.approve(id));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{id}/reject")
    public Result<Process> reject(@PathVariable Long id) {
        return Result.ok(processService.reject(id));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{id}/versions")
    public Result<Process> createVersion(@PathVariable Long id) {
        return Result.ok(processService.createVersion(id));
    }

    @GetMapping("/{id}/operations")
    public Result<List<ProcessOperation>> listOperations(@PathVariable Long id) {
        return Result.ok(processOperationService.list(id));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{id}/operations")
    public Result<ProcessOperation> createOperation(@PathVariable Long id,
                                                     @RequestBody ProcessOperation operation) {
        return Result.ok(processOperationService.create(id, operation));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}/operations/{operationId}")
    public Result<ProcessOperation> updateOperation(@PathVariable Long id, @PathVariable Long operationId,
                                                     @RequestBody ProcessOperation operation) {
        return Result.ok(processOperationService.update(id, operationId, operation));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}/operations/{operationId}")
    public Result<Void> deleteOperation(@PathVariable Long id, @PathVariable Long operationId) {
        processOperationService.delete(id, operationId);
        return Result.ok();
    }
}
