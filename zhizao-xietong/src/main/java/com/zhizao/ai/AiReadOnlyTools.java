package com.zhizao.ai;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhizao.dto.DeviceTelemetry;
import com.zhizao.dto.DeviceTelemetryPoint;
import com.zhizao.dto.ai.DeviceIssueContext;
import com.zhizao.entity.BomItem;
import com.zhizao.entity.Device;
import com.zhizao.entity.MaintenanceRecord;
import com.zhizao.entity.QualityRecord;
import com.zhizao.entity.WorkOrder;
import com.zhizao.mapper.MaintenanceRecordMapper;
import com.zhizao.mapper.QualityRecordMapper;
import com.zhizao.mapper.WorkOrderMapper;
import com.zhizao.service.BomItemService;
import com.zhizao.service.DeviceDataService;
import com.zhizao.service.DeviceService;
import com.zhizao.service.KitCheckResult;
import com.zhizao.service.KitCheckService;
import com.zhizao.service.WorkOrderService;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Agent 只读工具集。所有方法只调用现有查询能力，不提供任何写操作。
 *
 * <p>设备级查询先经过 {@link DeviceService#getById(Long)}，由车间数据域统一拦截越权；
 * 工单查询经过 {@link WorkOrderService#getById(Long)}，权限边界与人工页面一致。</p>
 *
 * @author Caspian
 */
@Component
public class AiReadOnlyTools {

    private static final int MAX_RECORDS = 20;

    private final DeviceService deviceService;
    private final DeviceDataService deviceDataService;
    private final WorkOrderService workOrderService;
    private final KitCheckService kitCheckService;
    private final BomItemService bomItemService;
    private final MaintenanceRecordMapper maintenanceRecordMapper;
    private final QualityRecordMapper qualityRecordMapper;
    private final WorkOrderMapper workOrderMapper;

    /**
     * 构造只读工具集。
     *
     * @param deviceService          设备查询
     * @param deviceDataService      遥测查询
     * @param workOrderService       工单查询
     * @param kitCheckService        齐套校验
     * @param bomItemService         BOM 查询
     * @param maintenanceRecordMapper 维修记录查询
     * @param qualityRecordMapper    质检记录查询
     * @param workOrderMapper        工单按设备查询
     */
    public AiReadOnlyTools(DeviceService deviceService, DeviceDataService deviceDataService,
                           WorkOrderService workOrderService, KitCheckService kitCheckService,
                           BomItemService bomItemService, MaintenanceRecordMapper maintenanceRecordMapper,
                           QualityRecordMapper qualityRecordMapper, WorkOrderMapper workOrderMapper) {
        this.deviceService = deviceService;
        this.deviceDataService = deviceDataService;
        this.workOrderService = workOrderService;
        this.kitCheckService = kitCheckService;
        this.bomItemService = bomItemService;
        this.maintenanceRecordMapper = maintenanceRecordMapper;
        this.qualityRecordMapper = qualityRecordMapper;
        this.workOrderMapper = workOrderMapper;
    }

    /**
     * 汇总设备异常归因所需的只读上下文。
     *
     * @param deviceId 设备主键
     * @param from     遥测起点，可为空
     * @param to       遥测终点，可为空
     * @return 只读上下文
     */
    public DeviceIssueContext loadDeviceIssue(Long deviceId, LocalDateTime from, LocalDateTime to) {
        Device device = deviceService.getById(deviceId);
        LocalDateTime end = to == null ? LocalDateTime.now() : to;
        LocalDateTime start = from == null ? end.minusDays(1) : from;
        DeviceTelemetry telemetry = deviceDataService.getTelemetry(deviceId);
        List<DeviceTelemetryPoint> recent = deviceDataService.history(deviceId, start, end, 50);
        List<MaintenanceRecord> maintenance = maintenanceRecordMapper.selectList(
                new LambdaQueryWrapper<MaintenanceRecord>()
                        .eq(MaintenanceRecord::getDeviceId, deviceId)
                        .ge(MaintenanceRecord::getMtTime, LocalDateTime.now().minusDays(180))
                        .orderByDesc(MaintenanceRecord::getMtTime)
                        .last("LIMIT " + MAX_RECORDS));
        List<WorkOrder> orders = workOrderMapper.selectList(
                new LambdaQueryWrapper<WorkOrder>()
                        .eq(WorkOrder::getDeviceId, deviceId)
                        .orderByDesc(WorkOrder::getId)
                        .last("LIMIT " + MAX_RECORDS));
        List<Long> orderIds = orders.stream().map(WorkOrder::getId).toList();
        List<QualityRecord> failures = orderIds.isEmpty() ? List.of() : qualityRecordMapper.selectList(
                new LambdaQueryWrapper<QualityRecord>()
                        .in(QualityRecord::getOrderId, orderIds)
                        .eq(QualityRecord::getResult, "FAIL")
                        .orderByDesc(QualityRecord::getInspectTime)
                        .last("LIMIT " + MAX_RECORDS));
        return new DeviceIssueContext(device, telemetry, recent, maintenance, failures, orders);
    }

    /**
     * 读取工单，权限边界与人工页面一致。
     *
     * @param workOrderId 工单主键
     * @return 工单
     */
    public WorkOrder loadWorkOrder(Long workOrderId) {
        return workOrderService.getById(workOrderId);
    }

    /**
     * 只读齐套校验。
     *
     * @param order 工单
     * @return 齐套结果
     */
    public KitCheckResult checkKit(WorkOrder order) {
        return kitCheckService.check(order);
    }

    /**
     * 只读 BOM 查询。
     *
     * @param processId 工艺版本主键，可为空
     * @return BOM 行
     */
    public List<BomItem> listBom(Long processId) {
        return processId == null ? List.of() : bomItemService.listByProcess(processId);
    }
}
