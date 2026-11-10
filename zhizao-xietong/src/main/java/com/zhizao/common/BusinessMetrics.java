package com.zhizao.common;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhizao.dto.MetricsOverview;
import com.zhizao.entity.Device;
import com.zhizao.entity.WorkOrder;
import com.zhizao.entity.WorkOrderStatusHistory;
import com.zhizao.enums.WorkOrderStatus;
import com.zhizao.mapper.DeviceMapper;
import com.zhizao.mapper.WorkOrderMapper;
import com.zhizao.mapper.WorkOrderStatusHistoryMapper;
import com.zhizao.service.KitCheckService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * 业务指标：向 actuator 暴露设备在线、遥测上报、报工与现场执行指标。
 *
 * <p>设备类指标在抓取时实时查询；齐套率涉及全部待开工工单的 BOM 核算，改为定时刷新到
 * {@link AtomicReference}，抓取只读缓存，保证 O(1)。空分母的指标返回
 * {@link Double#NaN}，表示「未定义」而不是 0；调用方需要自行判断。</p>
 *
 * @author Caspian
 */
@Component
public class BusinessMetrics {

    private final MeterRegistry meterRegistry;
    private final DeviceMapper deviceMapper;
    private final WorkOrderMapper workOrderMapper;
    private final WorkOrderStatusHistoryMapper statusHistoryMapper;
    private final KitCheckService kitCheckService;
    private final long onlineThresholdSeconds;

    private final Counter telemetryReports;
    private final Counter workOrderReports;
    private final Timer telemetryIngest;
    /** 齐套率缓存；首次定时刷新前为 NaN（未定义）。 */
    private final AtomicReference<Double> kitRate = new AtomicReference<>(Double.NaN);

    /**
     * 注册业务指标。
     *
     * @param meterRegistry           指标注册表
     * @param deviceMapper            设备数据访问
     * @param workOrderMapper         工单数据访问
     * @param statusHistoryMapper     工单状态历史数据访问
     * @param kitCheckService         齐套校验服务
     * @param onlineThresholdSeconds  在线判定阈值（秒）
     */
    public BusinessMetrics(MeterRegistry meterRegistry, DeviceMapper deviceMapper,
                           WorkOrderMapper workOrderMapper, WorkOrderStatusHistoryMapper statusHistoryMapper,
                           KitCheckService kitCheckService,
                           @Value("${device.online-threshold-seconds:90}") long onlineThresholdSeconds) {
        this.meterRegistry = meterRegistry;
        this.deviceMapper = deviceMapper;
        this.workOrderMapper = workOrderMapper;
        this.statusHistoryMapper = statusHistoryMapper;
        this.kitCheckService = kitCheckService;
        this.onlineThresholdSeconds = onlineThresholdSeconds;

        Gauge.builder("zhizao.devices.online", this, BusinessMetrics::onlineCount)
                .description("最近上报在阈值内的设备数").register(meterRegistry);
        Gauge.builder("zhizao.devices.total", this, BusinessMetrics::totalDevices)
                .description("设备台账总数").register(meterRegistry);
        Gauge.builder("zhizao.devices.online.rate", this, BusinessMetrics::onlineRate)
                .description("设备在线率 0-1；无设备时为 NaN").register(meterRegistry);
        Gauge.builder("zhizao.work_orders.takt.per_hour", this, BusinessMetrics::taktPerHour)
                .description("最近 24 小时完工合格数量 / 24 小时；无完工时为 0")
                .register(meterRegistry);
        Gauge.builder("zhizao.materials.kit_rate", kitRate::get)
                .description("待开工且已绑定工艺的工单中物料齐套占比；无此类工单时为 NaN。定时刷新，抓取只读缓存")
                .register(meterRegistry);

        this.telemetryReports = Counter.builder("zhizao.telemetry.reports")
                .description("已接受的设备遥测上报次数").register(meterRegistry);
        this.workOrderReports = Counter.builder("zhizao.work_orders.reports")
                .description("成功的生产报工次数").register(meterRegistry);
        this.telemetryIngest = Timer.builder("zhizao.telemetry.ingest")
                .description("设备遥测上报处理耗时").register(meterRegistry);
    }

    /** 记录一次被接受的设备遥测上报。 */
    public void telemetryReported() {
        telemetryReports.increment();
    }

    /** 记录一次成功的生产报工。 */
    public void workOrderReported() {
        workOrderReports.increment();
    }

    /**
     * 聚合当前业务指标。空分母项返回 {@code null} 与原因，不伪造 0 或 100%。
     *
     * @return 指标概览
     */
    public MetricsOverview overview() {
        long total = (long) totalDevices();
        long online = (long) onlineCount();
        long telemetryCount = (long) telemetryReports.count();
        long reportCount = (long) workOrderReports.count();
        double kit = kitRate.get();

        return new MetricsOverview(
                online,
                total,
                total == 0
                        ? MetricsOverview.MetricValue.undefined("暂无设备台账，在线率未定义")
                        : MetricsOverview.MetricValue.of((double) online / total),
                telemetryCount,
                telemetryIngest.count() == 0
                        ? MetricsOverview.MetricValue.undefined("暂无遥测上报，处理耗时未定义")
                        : MetricsOverview.MetricValue.of(telemetryIngest.mean(TimeUnit.MILLISECONDS)),
                reportCount,
                MetricsOverview.MetricValue.of(taktPerHour()),
                Double.isNaN(kit)
                        ? MetricsOverview.MetricValue.undefined("暂无待开工且已绑定工艺的工单，齐套率未定义")
                        : MetricsOverview.MetricValue.of(kit));
    }

    /**
     * 开始一次上报处理计时。
     *
     * @return 计时样本，需在 finally 中交给 {@link #stopIngest(Timer.Sample)}
     */
    public Timer.Sample startIngest() {
        return Timer.start(meterRegistry);
    }

    /**
     * 结束上报处理计时。
     *
     * @param sample 计时样本
     */
    public void stopIngest(Timer.Sample sample) {
        sample.stop(telemetryIngest);
    }

    private double onlineCount() {
        LocalDateTime since = LocalDateTime.now().minusSeconds(onlineThresholdSeconds);
        return deviceMapper.selectCount(new LambdaQueryWrapper<Device>()
                .ge(Device::getLastSeenAt, since));
    }

    private double totalDevices() {
        return deviceMapper.selectCount(null);
    }

    private double onlineRate() {
        double total = totalDevices();
        return total == 0 ? Double.NaN : onlineCount() / total;
    }

    private double taktPerHour() {
        LocalDateTime since = LocalDateTime.now().minusHours(24);
        List<WorkOrderStatusHistory> finished = statusHistoryMapper.selectList(
                new LambdaQueryWrapper<WorkOrderStatusHistory>()
                        .eq(WorkOrderStatusHistory::getToStatus, WorkOrderStatus.FINISHED.name())
                        .ge(WorkOrderStatusHistory::getCreatedAt, since));
        if (finished.isEmpty()) {
            return 0;
        }
        Set<Long> orderIds = finished.stream()
                .map(WorkOrderStatusHistory::getOrderId)
                .collect(Collectors.toSet());
        int qualified = workOrderMapper.selectBatchIds(orderIds).stream()
                .mapToInt(o -> o.getQtyQualified() == null ? 0 : o.getQtyQualified())
                .sum();
        return qualified / 24.0;
    }

    /**
     * 定时刷新齐套率缓存。
     *
     * <p>默认每分钟一次，可用 {@code metrics.kit-rate-refresh-ms} 调整。刷新会遍历「待开工且已绑定工艺」
     * 的工单并逐单核算 BOM，成本与待开工工单数相关；指标抓取本身只读缓存，不触发查询。</p>
     */
    @Scheduled(fixedDelayString = "${metrics.kit-rate-refresh-ms:60000}")
    public void refreshKitRate() {
        kitRate.set(computeKitRate());
    }

    private double computeKitRate() {
        List<WorkOrder> pending = workOrderMapper.selectList(new LambdaQueryWrapper<WorkOrder>()
                .eq(WorkOrder::getStatus, WorkOrderStatus.PENDING.name())
                .isNotNull(WorkOrder::getProcessId));
        if (pending.isEmpty()) {
            return Double.NaN;
        }
        long passed = pending.stream().filter(o -> kitCheckService.check(o).passed()).count();
        return (double) passed / pending.size();
    }
}
