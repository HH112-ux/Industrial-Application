package com.zhizao.controller;

import com.zhizao.common.PageResult;
import com.zhizao.common.Result;
import com.zhizao.entity.Device;
import com.zhizao.service.DeviceService;
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

@Tag(name = "设备管理")
@RestController
@RequestMapping("/api/devices")
public class DeviceController {

    private final DeviceService deviceService;

    public DeviceController(DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    @GetMapping
    public Result<PageResult<Device>> list(@RequestParam(defaultValue = "1") int pageNum,
                                           @RequestParam(defaultValue = "10") int pageSize,
                                           @RequestParam(required = false) String keyword,
                                           @RequestParam(required = false) String status) {
        return Result.ok(deviceService.list(pageNum, pageSize, keyword, status));
    }

    @GetMapping("/{id}")
    public Result<Device> get(@PathVariable Long id) {
        return Result.ok(deviceService.getById(id));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public Result<Device> create(@RequestBody Device device) {
        return Result.ok(deviceService.create(device));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public Result<Device> update(@PathVariable Long id, @RequestBody Device device) {
        device.setId(id);
        return Result.ok(deviceService.update(device));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        deviceService.delete(id);
        return Result.ok();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{id}/status")
    public Result<Device> changeStatus(@PathVariable Long id, @RequestParam String status) {
        return Result.ok(deviceService.changeStatus(id, status));
    }
}
