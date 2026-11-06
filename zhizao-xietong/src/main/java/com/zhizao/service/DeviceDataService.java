package com.zhizao.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhizao.common.BusinessException;
import com.zhizao.dto.DeviceGatewayReport;
import com.zhizao.dto.DeviceTelemetry;
import com.zhizao.entity.Device;
import com.zhizao.enums.DeviceStatus;
import com.zhizao.mapper.DeviceMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 设备数据服务：处理设备网关上报（MQTT/REST），维护最近一次遥测与设备在线时间。
 *
 * @author Caspian
 */
@Service
public class DeviceDataService {

    private static final Logger log = LoggerFactory.getLogger(DeviceDataService.class);

    private final DeviceMapper deviceMapper;
    private final DeviceIdempotencyGuard idempotencyGuard;
    private final Random random = new Random();

    /** 最近一次设备网关上报，key=设备编号。 */
    private final Map<String, LatestReport> latestReports = new ConcurrentHashMap<>();

    @Value("${device.simulation.enabled:false}")
    private boolean simulationEnabled;

    /**
     * 构造设备数据服务。
     *
     * @param deviceMapper       设备数据访问
     * @param idempotencyGuard   上报幂等守卫
     */
    public DeviceDataService(DeviceMapper deviceMapper, DeviceIdempotencyGuard idempotencyGuard) {
        this.deviceMapper = deviceMapper;
        this.idempotencyGuard = idempotencyGuard;
    }

    /**
     * 查询设备实时遥测。有真实上报时返回上报值；没有上报时按模拟开关决定返回模拟值还是空值。
     *
     * @param deviceId 设备主键
     * @return 实时遥测，`source` 标识数据来源
     * @throws BusinessException 设备不存在时抛出
     */
    public DeviceTelemetry getTelemetry(Long deviceId) {
        Device device = deviceMapper.selectById(deviceId);
        if (device == null) {
            throw new BusinessException("设备不存在");
        }
        LatestReport latest = latestReports.get(device.getDeviceNo());
        if (latest != null) {
            return toTelemetry(device, latest.report(), latest.source());
        }
        if (simulationEnabled) {
            return simulatedTelemetry(device);
        }
        return new DeviceTelemetry(device.getDeviceNo(), device.getDeviceName(), device.getStatus(),
                0, 0, 0, 0, null, "NONE");
    }

    /**
     * 处理设备网关上报，使用 MQTT 来源且不做幂等控制。
     *
     * @param report 设备上报内容
     * @throws BusinessException 设备不存在或状态非法时抛出
     */
    public void handleGatewayReport(DeviceGatewayReport report) {
        handleGatewayReport(report, null, "MQTT");
    }

    /**
     * 处理设备上报：幂等短路、更新设备状态与最近上报时间、缓存遥测。
     *
     * @param report    设备上报内容
     * @param ingestKey 设备侧幂等键，为空表示不去重
     * @param source    数据来源，取值 MQTT 或 HTTP
     * @throws BusinessException 设备不存在或状态非法时抛出
     */
    public void handleGatewayReport(DeviceGatewayReport report, String ingestKey, String source) {
        String dedupeKey = StringUtils.hasText(ingestKey) ? report.deviceNo() + ":" + ingestKey : null;
        if (!idempotencyGuard.acquire(dedupeKey)) {
            log.info("设备上报重复，已忽略：deviceNo={}，ingestKey={}", report.deviceNo(), ingestKey);
            return;
        }
        Device device = requireDevice(report.deviceNo());
        if (StringUtils.hasText(report.status())) {
            device.setStatus(normalizeStatus(report.status()));
        }
        device.setLastSeenAt(LocalDateTime.now());
        deviceMapper.updateById(device);
        latestReports.put(report.deviceNo(), new LatestReport(report, source));
    }

    private DeviceTelemetry toTelemetry(Device device, DeviceGatewayReport report, String source) {
        boolean running = DeviceStatus.RUNNING.name().equals(report.status());
        double temperature = report.temperature() == null ? 0 : report.temperature();
        int rpm = report.rpm() == null ? 0 : report.rpm().intValue();
        double current = report.current() == null ? 0 : report.current();
        double oee = round1(running ? 60 + random.nextDouble() * 35 : 0); // OEE 仍需可用率/性能/质量，此处估算
        return new DeviceTelemetry(device.getDeviceNo(), device.getDeviceName(),
                report.status() == null ? device.getStatus() : report.status(),
                temperature, rpm, current, oee, LocalDateTime.now(), source);
    }

    private DeviceTelemetry simulatedTelemetry(Device device) {
        boolean running = DeviceStatus.RUNNING.name().equals(device.getStatus());
        double temperature = round1(running ? 40 + random.nextDouble() * 30 : 20 + random.nextDouble() * 5);
        int rpm = running ? 800 + random.nextInt(1600) : 0;
        double current = round1(running ? 5 + random.nextDouble() * 20 : 0);
        double oee = round1(running ? 60 + random.nextDouble() * 35 : 0);
        return new DeviceTelemetry(device.getDeviceNo(), device.getDeviceName(), device.getStatus(),
                temperature, rpm, current, oee, LocalDateTime.now(), "SIMULATED");
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

    /**
     * 最近一次上报及其来源。
     *
     * @param report 上报内容
     * @param source 数据来源 MQTT/HTTP
     */
    private record LatestReport(DeviceGatewayReport report, String source) {
    }
}
