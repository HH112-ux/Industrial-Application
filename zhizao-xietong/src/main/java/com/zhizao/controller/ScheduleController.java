package com.zhizao.controller;

import com.zhizao.common.Result;
import com.zhizao.dto.ScheduleEntryView;
import com.zhizao.dto.ScheduleRunResult;
import com.zhizao.entity.WorkOrder;
import com.zhizao.service.ScheduleService;
import com.zhizao.service.WorkOrderService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 有限产能排程接口：重算待排工单的排程，并查询单张工单的排程依据。
 *
 * @author Caspian
 */
@Tag(name = "计划排程")
@RestController
@RequestMapping("/api/schedules")
public class ScheduleController {

    private final ScheduleService scheduleService;
    private final WorkOrderService workOrderService;

    /**
     * 构造排程接口。
     *
     * @param scheduleService  排程服务
     * @param workOrderService 工单服务，用于校验车间数据域
     */
    public ScheduleController(ScheduleService scheduleService, WorkOrderService workOrderService) {
        this.scheduleService = scheduleService;
        this.workOrderService = workOrderService;
    }

    /**
     * 重算全部「待开工」工单的排程。幂等：同一批工单与同一份产能数据重复调用结果一致。
     *
     * @return 成功排入与无法排入的明细
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/run")
    public Result<ScheduleRunResult> run() {
        return Result.ok(scheduleService.run());
    }

    /**
     * 查询某张工单的排程结果，包含设备、时间窗、队列位置与位置依据。
     *
     * @param workOrderId 工单主键
     * @return 排程明细，含工单号、产品与设备编号
     */
    @GetMapping("/work-orders/{workOrderId}")
    public Result<List<ScheduleEntryView>> entries(@PathVariable Long workOrderId) {
        WorkOrder order = workOrderService.getById(workOrderId);
        return Result.ok(scheduleService.entries(order));
    }

    /**
     * 查询设备维度的排程时间轴，用于查看同一设备上多张工单的先后与占用区间。
     *
     * <p>只返回当前用户车间范围内设备的已排入记录；非管理员跨车间不可见。</p>
     *
     * @param from 起始日期（含）
     * @param to   结束日期（含），跨度最长 31 天
     * @return 按设备编号与开始时间排序的排程明细
     */
    @GetMapping("/devices")
    public Result<List<ScheduleEntryView>> deviceTimeline(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return Result.ok(scheduleService.deviceTimeline(from, to));
    }
}
