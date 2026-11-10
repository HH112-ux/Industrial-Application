package com.zhizao.common;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zhizao.dto.MetricsOverview;
import com.zhizao.entity.Device;
import com.zhizao.entity.WorkOrder;
import com.zhizao.entity.WorkOrderStatusHistory;
import com.zhizao.mapper.DeviceMapper;
import com.zhizao.mapper.WorkOrderMapper;
import com.zhizao.mapper.WorkOrderStatusHistoryMapper;
import com.zhizao.service.KitCheckResult;
import com.zhizao.service.KitCheckService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 业务指标测试：空分母返回未定义，计数与节拍按真实数据计算。
 *
 * @author Caspian
 */
@ExtendWith(MockitoExtension.class)
class BusinessMetricsTest {

    static {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Device.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), WorkOrder.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                WorkOrderStatusHistory.class);
    }

    @Mock
    private DeviceMapper deviceMapper;
    @Mock
    private WorkOrderMapper workOrderMapper;
    @Mock
    private WorkOrderStatusHistoryMapper statusHistoryMapper;
    @Mock
    private KitCheckService kitCheckService;

    private SimpleMeterRegistry registry;
    private BusinessMetrics metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new BusinessMetrics(registry, deviceMapper, workOrderMapper, statusHistoryMapper,
                kitCheckService, 90);
    }

    @Test
    void onlineRateIsUndefinedWithoutDevices() {
        when(deviceMapper.selectCount(any())).thenReturn(0L);

        double rate = registry.get("zhizao.devices.online.rate").gauge().value();

        assertTrue(Double.isNaN(rate));
    }

    @Test
    void onlineRateUsesOnlineOverTotal() {
        when(deviceMapper.selectCount(any())).thenReturn(5L).thenReturn(2L);

        double rate = registry.get("zhizao.devices.online.rate").gauge().value();

        assertEquals(0.4, rate, 0.0001);
    }

    @Test
    void kitRateIsUndefinedWithoutPendingOrders() {
        when(workOrderMapper.selectList(any())).thenReturn(List.of());
        metrics.refreshKitRate();

        double rate = registry.get("zhizao.materials.kit_rate").gauge().value();

        assertTrue(Double.isNaN(rate));
    }

    @Test
    void kitRateCountsOnlyPassedOrders() {
        WorkOrder passed = new WorkOrder();
        passed.setId(1L);
        passed.setProcessId(10L);
        WorkOrder shortage = new WorkOrder();
        shortage.setId(2L);
        shortage.setProcessId(11L);
        when(workOrderMapper.selectList(any())).thenReturn(List.of(passed, shortage));
        when(kitCheckService.check(passed)).thenReturn(KitCheckResult.pass());
        when(kitCheckService.check(shortage)).thenReturn(
                new KitCheckResult(false, List.of(new KitCheckResult.ShortageItem("M1", "钢板", 5, 1)), null));
        metrics.refreshKitRate();

        double rate = registry.get("zhizao.materials.kit_rate").gauge().value();

        assertEquals(0.5, rate, 0.0001);
    }

    @Test
    void kitRateGaugeReadsCacheWithoutQueryingOnEveryScrape() {
        when(workOrderMapper.selectList(any())).thenReturn(List.of());
        metrics.refreshKitRate();

        for (int i = 0; i < 5; i++) {
            registry.get("zhizao.materials.kit_rate").gauge().value();
        }

        verify(workOrderMapper, times(1)).selectList(any());
    }

    @Test
    void taktUsesFinishedQualifiedQtyInLastDay() {
        WorkOrderStatusHistory history = new WorkOrderStatusHistory();
        history.setOrderId(1L);
        when(statusHistoryMapper.selectList(any())).thenReturn(List.of(history));
        WorkOrder order = new WorkOrder();
        order.setId(1L);
        order.setQtyQualified(48);
        when(workOrderMapper.selectBatchIds(any())).thenReturn(List.of(order));

        double takt = registry.get("zhizao.work_orders.takt.per_hour").gauge().value();

        assertEquals(2.0, takt, 0.0001);
    }

    @Test
    void overviewExplainsUndefinedMetrics() {
        when(deviceMapper.selectCount(any())).thenReturn(0L);
        when(workOrderMapper.selectList(any())).thenReturn(List.of());
        when(statusHistoryMapper.selectList(any())).thenReturn(List.of());
        metrics.refreshKitRate();

        MetricsOverview overview = metrics.overview();

        assertEquals(0, overview.devicesTotal());
        assertNull(overview.deviceOnlineRate().value());
        assertNotNull(overview.deviceOnlineRate().reason());
        assertNull(overview.telemetryIngestAvgMs().value());
        assertNotNull(overview.telemetryIngestAvgMs().reason());
        assertEquals(0.0, overview.workOrderTaktPerHour().value(), 0.0001);
        assertNull(overview.materialKitRate().value());
        assertNotNull(overview.materialKitRate().reason());
    }

    @Test
    void countersTrackAcceptedReports() {
        metrics.telemetryReported();
        metrics.telemetryReported();
        metrics.workOrderReported();

        assertEquals(2.0, registry.get("zhizao.telemetry.reports").counter().count());
        assertEquals(1.0, registry.get("zhizao.work_orders.reports").counter().count());
    }
}
