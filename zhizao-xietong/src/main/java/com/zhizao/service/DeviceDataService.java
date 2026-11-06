package com.zhizao.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhizao.common.BadRequestException;
import com.zhizao.common.BusinessException;
import com.zhizao.common.BusinessMetrics;
import com.zhizao.dto.DeviceGatewayReport;
import com.zhizao.dto.DeviceTelemetry;
import com.zhizao.dto.DeviceTelemetryPoint;
import com.zhizao.entity.Device;
import com.zhizao.entity.DeviceTelemetryRecord;
import com.zhizao.enums.DeviceStatus;
import com.zhizao.mapper.DeviceMapper;
import com.zhizao.mapper.DeviceTelemetryMapper;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 设备数据服务：处理设备网关上报（MQTT/REST），维护最近一次遥测、设备在线时间，
 * 并把每次真实上报写入时序表供历史查询。
 *
 * @author Caspian
 */
@Service
public class DeviceDataService {

    private static final Logger log = LoggerFactory.getLogger(DeviceDataService.class);

    /** 历史查询未指定条数时的默认上限。 */
    private static final int DEFAULT_HISTORY_LIMIT = 500;

    /** 历史查询允许的最大条数，超出按上限处理。 */
    private static final int MAX_HISTORY_LIMIT = 1000;

    private final DeviceMapper deviceMapper;
    private final DeviceTelemetryMapper telemetryMapper;
    private final DeviceIdempotencyGuard idempotencyGuard;
    private final BusinessMetrics businessMetrics;
    private final Random random = new Random();

    /** 最近一次设备网关上报，key=设备编号。 */
    private final Map<String, LatestReport> latestReports = new ConcurrentHashMap<>();

    @Value("${device.simulation.enabled:false}")
    private boolean simulationEnabled;

    /**
     * 构造设备数据服务。
     *
     * @param deviceMapper     设备数据访问
     * @param telemetryMapper  遥测时序数据访问
     * @param idempotencyGuard 上报幂等守卫
     * @param businessMetrics  业务指标
     */
    public DeviceDataService(DeviceMapper deviceMapper, DeviceTelemetryMapper telemetryMapper,
                             DeviceIdempotencyGuard idempotencyGuard, BusinessMetrics businessMetrics) {
        this.deviceMapper = deviceMapper;
        this.telemetryMapper = telemetryMapper;
        this.idempotencyGuard = idempotencyGuard;
        this.businessMetrics = businessMetrics;
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
     * 查询设备遥测历史，按上报时间升序返回。
     *
     * <p>不传时间范围时默认最近 24 小时；条数默认 500，超过 1000 按 1000 处理。</p>
     *
     * @param deviceId 设备主键
     * @param from     起始时间（含），为空表示当前时间前 24 小时
     * @param to       结束时间（含），为空表示当前时间
     * @param limit    返回条数上限，非正数按默认值处理
     * @return 升序排列的遥测采样
     * @throws BusinessException    设备不存在时抛出
     * @throws BadRequestException  起始时间晚于结束时间时抛出
     */
    public List<DeviceTelemetryPoint> history(Long deviceId, LocalDateTime from, LocalDateTime to, Integer limit) {
        Device device = deviceMapper.selectById(deviceId);
        if (device == null) {
            throw new BusinessException("设备不存在");
        }
        LocalDateTime end = to == null ? LocalDateTime.now() : to;
        LocalDateTime start = from == null ? end.minusHours(24) : from;
        if (start.isAfter(end)) {
            throw new BadRequestException("起始时间不能晚于结束时间");
        }
        int size = limit == null || limit <= 0 ? DEFAULT_HISTORY_LIMIT : Math.min(limit, MAX_HISTORY_LIMIT);
        List<DeviceTelemetryRecord> records = telemetryMapper.selectList(
                new LambdaQueryWrapper<DeviceTelemetryRecord>()
                        .eq(DeviceTelemetryRecord::getDeviceId, deviceId)
                        .ge(DeviceTelemetryRecord::getReportedAt, start)
                        .le(DeviceTelemetryRecord::getReportedAt, end)
                        .orderByAsc(DeviceTelemetryRecord::getReportedAt)
                        .last("LIMIT " + size));
        return records.stream()
                .map(r -> new DeviceTelemetryPoint(r.getReportedAt(), r.getTemperature(), r.getRpm(),
                        r.getCurrent(), r.getStatus(), r.getSource()))
                .toList();
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
     * 处理设备上报：幂等短路、更新设备状态与最近上报时间、写入时序表并缓存遥测。
     *
     * <p>时序写入失败不影响设备状态更新：按约定遥测可以丢弃，只记录告警日志。</p>
     *
     * @param report    设备上报内容
     * @param ingestKey 设备侧幂等键，为空表示不去重
     * @param source    数据来源，取值 MQTT 或 HTTP
     * @throws BusinessException 设备不存在或状态非法时抛出
     */
    public void handleGatewayReport(DeviceGatewayReport report, String ingestKey, String source) {
        Timer.Sample sample = businessMetrics.startIngest();
        try {
            String dedupeKey = StringUtils.hasText(ingestKey) ? report.deviceNo() + ":" + ingestKey : null;
            if (!idempotencyGuard.acquire(dedupeKey)) {
                log.info("设备上报重复，已忽略：deviceNo={}，ingestKey={}", report.deviceNo(), ingestKey);
                return;
            }
            Device device = requireDevice(report.deviceNo());
            if (StringUtils.hasText(report.status())) {
                device.setStatus(normalizeStatus(report.status()));
            }
            LocalDateTime receivedAt = LocalDateTime.now();
            device.setLastSeenAt(receivedAt);
            deviceMapper.updateById(device);
            persistTelemetry(device, report, ingestKey, source, receivedAt);
            latestReports.put(report.deviceNo(), new LatestReport(report, source));
            businessMetrics.telemetryReported();
        } finally {
            businessMetrics.stopIngest(sample);
        }
    }

    /**
     * 把一次上报写入遥测时序表。幂等键冲突视为重复上报，静默忽略。
     *
     * @param device     已确认存在的设备
     * @param report     上报内容
     * @param ingestKey  幂等键，可为空
     * @param source     数据来源
     * @param receivedAt 服务端接收时间
     */
    private void persistTelemetry(Device device, DeviceGatewayReport report, String ingestKey,
                                  String source, LocalDateTime receivedAt) {
        DeviceTelemetryRecord record = new DeviceTelemetryRecord();
        record.setDeviceId(device.getId());
        record.setDeviceNo(device.getDeviceNo());
        record.setStatus(StringUtils.hasText(report.status()) ? report.status() : device.getStatus());
        record.setTemperature(report.temperature());
        record.setRpm(report.rpm() == null ? null : report.rpm().intValue());
        record.setCurrent(report.current());
        record.setReportedAt(toReportedAt(report.timestamp(), receivedAt));
        record.setReceivedAt(receivedAt);
        record.setIngestKey(StringUtils.hasText(ingestKey) ? ingestKey : null);
        record.setSource(source);
        try {
            telemetryMapper.insert(record);
        } catch (DuplicateKeyException e) {
            log.info("设备上报幂等键已存在，跳过时序写入：deviceNo={}，ingestKey={}",
                    report.deviceNo(), ingestKey);
        } catch (RuntimeException e) {
            log.warn("设备遥测写入失败，已忽略：deviceNo={}，原因={}", report.deviceNo(), e.getMessage());
        }
    }

    /**
     * 计算上报时间：设备携带毫秒时间戳时按其换算，否则使用服务端接收时间。
     *
     * @param timestamp  设备时间戳（毫秒），可为空
     * @param receivedAt 服务端接收时间
     * @return 用于落库的上报时间
     */
    private LocalDateTime toReportedAt(Long timestamp, LocalDateTime receivedAt) {
        if (timestamp == null || timestamp <= 0) {
            return receivedAt;
        }
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault());
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
