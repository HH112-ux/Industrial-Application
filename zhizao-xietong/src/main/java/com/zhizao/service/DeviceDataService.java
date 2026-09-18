package com.zhizao.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhizao.common.BusinessException;
import com.zhizao.dto.DeviceGatewayReport;
import com.zhizao.dto.DeviceStatusPushRequest;
import com.zhizao.dto.DeviceTelemetry;
import com.zhizao.entity.Device;
import com.zhizao.enums.DeviceStatus;
import com.zhizao.mapper.DeviceMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 设备数据服务：处理设备网关上报（MQTT/REST），提供遥测查询。
 */
@Service
public class DeviceDataService {

    private final DeviceMapper deviceMapper;
    private final Random random = new Random();
    /** 最近一次设备网关上报，key=设备编号。 */
    private final Map<String, DeviceGatewayReport> latestReports = new ConcurrentHashMap<>();

    public DeviceDataService(DeviceMapper deviceMapper) {
        this.deviceMapper = deviceMapper;
    }

    public DeviceTelemetry getTelemetry(Long deviceId) {
        Device device = deviceMapper.selectById(deviceId);
        if (device == null) {
            throw new BusinessException("设备不存在");
        }
        DeviceGatewayReport report = latestReports.get(device.getDeviceNo());
        if (report != null) {
            return toTelemetry(device, report);
        }
        // 无真实上报时返回模拟遥测
        boolean running = DeviceStatus.RUNNING.name().equals(device.getStatus());
        double temperature = round1(running ? 40 + random.nextDouble() * 30 : 20 + random.nextDouble() * 5);
        int rpm = running ? 800 + random.nextInt(1600) : 0;
        double current = round1(running ? 5 + random.nextDouble() * 20 : 0);
        double oee = round1(running ? 60 + random.nextDouble() * 35 : 0);
        return new DeviceTelemetry(device.getDeviceNo(), device.getDeviceName(), device.getStatus(),
                temperature, rpm, current, oee, LocalDateTime.now());
    }

    /** 模拟设备端推送状态（REST 集成层接口）。 */
    public void pushStatus(DeviceStatusPushRequest req) {
        Device device = requireDevice(req.deviceNo());
        device.setStatus(normalizeStatus(req.status()));
        deviceMapper.updateById(device);
    }

    /**
     * 设备网关（MQTT）上报：更新状态并缓存最新遥测。
     */
    public void handleGatewayReport(DeviceGatewayReport report) {
        Device device = requireDevice(report.deviceNo());
        if (report.status() != null && !report.status().isBlank()) {
            device.setStatus(normalizeStatus(report.status()));
            deviceMapper.updateById(device);
        }
        latestReports.put(report.deviceNo(), report);
    }

    private DeviceTelemetry toTelemetry(Device device, DeviceGatewayReport report) {
        boolean running = DeviceStatus.RUNNING.name().equals(report.status());
        double temperature = report.temperature() == null ? 0 : report.temperature();
        int rpm = report.rpm() == null ? 0 : report.rpm().intValue();
        double current = report.current() == null ? 0 : report.current();
        double oee = round1(running ? 60 + random.nextDouble() * 35 : 0); // OEE 仍需可用率/性能/质量，此处估算
        return new DeviceTelemetry(device.getDeviceNo(), device.getDeviceName(),
                report.status() == null ? device.getStatus() : report.status(),
                temperature, rpm, current, oee, LocalDateTime.now());
    }

    private Device requireDevice(String deviceNo) {
        Device device = deviceMapper.selectOne(
                new LambdaQueryWrapper<Device>().eq(Device::getDeviceNo, deviceNo));
        if (device == null) {
            throw new BusinessException("设备不存在：" + deviceNo);
        }
        return device;
    }

    private String normalizeStatus(String status) {
        try {
            return DeviceStatus.valueOf(status).name();
        } catch (Exception e) {
            throw new BusinessException("非法设备状态：" + status);
        }
    }

    private double round1(double v) {
        return Math.round(v * 10) / 10.0;
    }
}
