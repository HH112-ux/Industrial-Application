package com.zhizao.controller;

import com.zhizao.common.PageResult;
import com.zhizao.common.Result;
import com.zhizao.entity.MaintenanceRecord;
import com.zhizao.service.MaintenanceService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "设备点检/维修")
@RestController
@RequestMapping("/api/maintenance")
public class MaintenanceController {

    private final MaintenanceService maintenanceService;

    public MaintenanceController(MaintenanceService maintenanceService) {
        this.maintenanceService = maintenanceService;
    }

    @GetMapping
    public Result<PageResult<MaintenanceRecord>> list(@RequestParam(defaultValue = "1") int pageNum,
                                                      @RequestParam(defaultValue = "10") int pageSize,
                                                      @RequestParam(required = false) String keyword,
                                                      @RequestParam(required = false) String mtType) {
        return Result.ok(maintenanceService.list(pageNum, pageSize, keyword, mtType));
    }

    @GetMapping("/{id}")
    public Result<MaintenanceRecord> get(@PathVariable Long id) {
        return Result.ok(maintenanceService.getById(id));
    }

    @PostMapping
    public Result<MaintenanceRecord> create(@RequestBody MaintenanceRecord record) {
        return Result.ok(maintenanceService.create(record));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public Result<MaintenanceRecord> update(@PathVariable Long id, @RequestBody MaintenanceRecord record) {
        return Result.ok(maintenanceService.update(id, record));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        maintenanceService.delete(id);
        return Result.ok();
    }
}
