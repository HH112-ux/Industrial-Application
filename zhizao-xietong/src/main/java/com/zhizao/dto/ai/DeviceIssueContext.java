package com.zhizao.dto.ai;

import com.zhizao.entity.Device;
import com.zhizao.entity.MaintenanceRecord;
import com.zhizao.entity.QualityRecord;
import com.zhizao.entity.WorkOrder;
import com.zhizao.dto.DeviceTelemetry;
import com.zhizao.dto.DeviceTelemetryPoint;

import java.util.List;

/**
 * 设备异常归因的只读输入快照。模型只能看到这些字段，不接触写服务。
 *
 * @param device        设备台账
 * @param telemetry     最新遥测
 * @param recent        时间窗内的遥测采样
 * @param maintenance   最近维修记录
 * @param qualityFail   最近质检不合格记录
 * @param workOrders    该设备相关工单
 * @author Caspian
 */
public record DeviceIssueContext(Device device,
                                 DeviceTelemetry telemetry,
                                 List<DeviceTelemetryPoint> recent,
                                 List<MaintenanceRecord> maintenance,
                                 List<QualityRecord> qualityFail,
                                 List<WorkOrder> workOrders) {
}
