package com.zhizao.service;

import com.zhizao.common.BadRequestException;
import com.zhizao.common.BusinessException;
import com.zhizao.common.BusinessMetrics;
import com.zhizao.common.ForbiddenException;
import com.zhizao.dto.DeviceGatewayReport;
import com.zhizao.dto.DeviceTelemetry;
import com.zhizao.dto.DeviceTelemetryPoint;
import com.zhizao.dto.TelemetryHistory;
import com.zhizao.entity.Device;
import com.zhizao.entity.DeviceTelemetryRecord;
import com.zhizao.mapper.DeviceMapper;
import com.zhizao.mapper.DeviceTelemetryMapper;
import com.zhizao.security.DataScope;
import com.zhizao.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 设备数据服务测试：上报落库、在线时间、幂等短路与遥测来源。
 *
 * @author Caspian
 */
@ExtendWith(MockitoExtension.class)
class DeviceDataServiceTest {

    @Mock
    private DeviceMapper deviceMapper;

    @Mock
    private DeviceTelemetryMapper telemetryMapper;

    @Mock
    private DeviceIdempotencyGuard idempotencyGuard;

    @Mock
    private BusinessMetrics businessMetrics;

    @Mock
    private DeviceService deviceService;

    @Mock
    private AlarmEvaluationService alarmEvaluationService;

    private DeviceDataService deviceDataService;

    /**
     * 构造被测服务，默认放行所有幂等键。
     */
    @BeforeEach
    void setUp() {
        deviceDataService = new DeviceDataService(deviceMapper, telemetryMapper, idempotencyGuard,
                businessMetrics, deviceService, alarmEvaluationService);
        lenient().when(idempotencyGuard.acquire(any())).thenReturn(true);
    }

    private Device device() {
        Device d = new Device();
        d.setId(1L);
        d.setDeviceNo("EQ-202609-0001");
        d.setDeviceName("数控车床");
        d.setStatus("STOPPED");
        return d;
    }

    @Test
    void handleReportUpdatesStatusAndCachesTelemetry() {
        when(deviceMapper.selectOne(any())).thenReturn(device());
        when(deviceService.getById(1L)).thenReturn(device());

        deviceDataService.handleGatewayReport(
                new DeviceGatewayReport("EQ-202609-0001", "RUNNING", 45.0, 1500.0, 8.5));

        DeviceTelemetry t = deviceDataService.getTelemetry(1L);
        assertEquals("RUNNING", t.status());
        assertEquals(45.0, t.temperature(), 0.01);
        assertEquals(1500, t.rpm());
        assertEquals(8.5, t.current(), 0.01);
        assertEquals("MQTT", t.source());
        verify(deviceMapper).updateById(any(Device.class));
    }

    @Test
    void handleReportRecordsLastSeenAt() {
        Device stored = device();
        when(deviceMapper.selectOne(any())).thenReturn(stored);

        deviceDataService.handleGatewayReport(
                new DeviceGatewayReport("EQ-202609-0001", "RUNNING", 45.0, 1500.0, 8.5));

        ArgumentCaptor<Device> captor = ArgumentCaptor.forClass(Device.class);
        verify(deviceMapper).updateById(captor.capture());
        assertNotNull(captor.getValue().getLastSeenAt());
        assertEquals("RUNNING", captor.getValue().getStatus());
    }

    @Test
    void duplicateIngestKeyIsIgnored() {
        when(idempotencyGuard.acquire("EQ-202609-0001:k-1")).thenReturn(false);

        deviceDataService.handleGatewayReport(
                new DeviceGatewayReport("EQ-202609-0001", "RUNNING", 45.0, 1500.0, 8.5, null, null, null, null, "k-1"),
                "k-1", "HTTP");

        verify(deviceMapper, never()).updateById(any(Device.class));
        verify(telemetryMapper, never()).insert(any(DeviceTelemetryRecord.class));
    }

    @Test
    void sameIngestKeyFromDifferentDevicesIsPersistedForBoth() {
        Device first = device();
        Device second = device();
        second.setId(2L);
        second.setDeviceNo("EQ-202609-0002");
        when(deviceMapper.selectOne(any())).thenReturn(first, second);

        deviceDataService.handleGatewayReport(
                new DeviceGatewayReport("EQ-202609-0001", "RUNNING", 45.0, 1500.0, 8.5,
                        null, null, null, null, "k-shared"), "k-shared", "HTTP");
        deviceDataService.handleGatewayReport(
                new DeviceGatewayReport("EQ-202609-0002", "RUNNING", 46.0, 1600.0, 9.0,
                        null, null, null, null, "k-shared"), "k-shared", "HTTP");

        verify(idempotencyGuard).acquire("EQ-202609-0001:k-shared");
        verify(idempotencyGuard).acquire("EQ-202609-0002:k-shared");
        ArgumentCaptor<DeviceTelemetryRecord> captor = ArgumentCaptor.forClass(DeviceTelemetryRecord.class);
        verify(telemetryMapper, times(2)).insert(captor.capture());
        assertEquals(List.of(1L, 2L), captor.getAllValues().stream()
                .map(DeviceTelemetryRecord::getDeviceId).toList());
        assertEquals(List.of("k-shared", "k-shared"), captor.getAllValues().stream()
                .map(DeviceTelemetryRecord::getIngestKey).toList());
    }

    @Test
    void reportIsPersistedToTelemetryTable() {
        when(deviceMapper.selectOne(any())).thenReturn(device());

        deviceDataService.handleGatewayReport(
                new DeviceGatewayReport("EQ-202609-0001", "RUNNING", 45.5, 1500.0, 8.5, null, null, null, null, "k-2"),
                "k-2", "HTTP");

        ArgumentCaptor<DeviceTelemetryRecord> captor = ArgumentCaptor.forClass(DeviceTelemetryRecord.class);
        verify(telemetryMapper).insert(captor.capture());
        DeviceTelemetryRecord record = captor.getValue();
        assertEquals(1L, record.getDeviceId());
        assertEquals("EQ-202609-0001", record.getDeviceNo());
        assertEquals("RUNNING", record.getStatus());
        assertEquals(45.5, record.getTemperature(), 0.01);
        assertEquals(1500, record.getRpm());
        assertEquals(8.5, record.getCurrent(), 0.01);
        assertEquals("HTTP", record.getSource());
        assertEquals("k-2", record.getIngestKey());
        assertNotNull(record.getReportedAt());
        assertNotNull(record.getReceivedAt());
    }

    @Test
    void reportUsesDeviceTimestampWhenPresent() {
        when(deviceMapper.selectOne(any())).thenReturn(device());
        long millis = 1_760_000_000_000L;

        deviceDataService.handleGatewayReport(
                new DeviceGatewayReport("EQ-202609-0001", "RUNNING", 45.5, 1500.0, 8.5,
                        null, millis, null, null, null),
                null, "MQTT");

        ArgumentCaptor<DeviceTelemetryRecord> captor = ArgumentCaptor.forClass(DeviceTelemetryRecord.class);
        verify(telemetryMapper).insert(captor.capture());
        assertEquals(java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime(),
                captor.getValue().getReportedAt());
        assertNull(captor.getValue().getIngestKey());
    }

    @Test
    void historyRejectsInvertedRange() {
        when(deviceService.getById(1L)).thenReturn(device());

        LocalDateTime from = LocalDateTime.now();
        assertThrows(BadRequestException.class,
                () -> deviceDataService.history(1L, from, from.minusHours(1), 10));
    }

    @Test
    void historyUnknownDeviceThrows() {
        when(deviceService.getById(9L)).thenThrow(new BusinessException("设备不存在"));

        assertThrows(BusinessException.class,
                () -> deviceDataService.history(9L, null, null, 10));
    }

    @Test
    void telemetryAndHistoryRejectDeviceOutsideWorkshop() {
        Device otherWorkshop = device();
        otherWorkshop.setWorkshop("二车间");
        when(deviceMapper.selectById(1L)).thenReturn(otherWorkshop);
        DeviceService scopedDeviceService = new DeviceService(deviceMapper, mock(CodeGenerator.class), new DataScope());
        DeviceDataService scoped = new DeviceDataService(deviceMapper, telemetryMapper, idempotencyGuard,
                businessMetrics, scopedDeviceService, alarmEvaluationService);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new UserPrincipal("lisi", "USER", "一车间"), null, List.of()));
        try {
            assertThrows(ForbiddenException.class, () -> scoped.getTelemetry(1L));
            assertThrows(ForbiddenException.class, () -> scoped.history(1L, null, null, 10));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void historyMapsRecordsToPoints() {
        when(deviceService.getById(1L)).thenReturn(device());
        DeviceTelemetryRecord record = new DeviceTelemetryRecord();
        record.setReportedAt(LocalDateTime.of(2026, 11, 6, 10, 0));
        record.setTemperature(45.2);
        record.setRpm(1500);
        record.setCurrent(8.5);
        record.setStatus("RUNNING");
        record.setSource("MQTT");
        when(telemetryMapper.selectList(any())).thenReturn(List.of(record));

        List<DeviceTelemetryPoint> points = deviceDataService.history(1L, null, null, 5000);

        assertEquals(1, points.size());
        DeviceTelemetryPoint point = points.get(0);
        assertEquals(LocalDateTime.of(2026, 11, 6, 10, 0), point.reportedAt());
        assertEquals(45.2, point.temperature(), 0.01);
        assertEquals(1500, point.rpm());
        assertEquals("RUNNING", point.status());
        assertEquals("MQTT", point.source());
    }

    @Test
    void telemetryWriteFailureDoesNotBlockStatusUpdate() {
        when(deviceMapper.selectOne(any())).thenReturn(device());
        when(deviceService.getById(1L)).thenReturn(device());
        when(telemetryMapper.insert(any(DeviceTelemetryRecord.class)))
                .thenThrow(new RuntimeException("db down"));

        deviceDataService.handleGatewayReport(
                new DeviceGatewayReport("EQ-202609-0001", "RUNNING", 45.0, 1500.0, 8.5));

        verify(deviceMapper).updateById(any(Device.class));
        assertEquals("MQTT", deviceDataService.getTelemetry(1L).source());
    }

    @Test
    void alarmEvaluationFailureDoesNotBreakIngest() {
        when(deviceMapper.selectOne(any())).thenReturn(device());
        when(deviceService.getById(1L)).thenReturn(device());
        doThrow(new RuntimeException("alarm down"))
                .when(alarmEvaluationService).evaluateThreshold(any(), any());

        deviceDataService.handleGatewayReport(
                new DeviceGatewayReport("EQ-202609-0001", "RUNNING", 45.0, 1500.0, 8.5));

        verify(telemetryMapper).insert(any(DeviceTelemetryRecord.class));
        verify(deviceMapper).updateById(any(Device.class));
        assertEquals("MQTT", deviceDataService.getTelemetry(1L).source());
    }

    @Test
    void handleReportUnknownDeviceThrows() {
        when(deviceMapper.selectOne(any())).thenReturn(null);

        assertThrows(BusinessException.class, () -> deviceDataService.handleGatewayReport(
                new DeviceGatewayReport("EQ-999", "RUNNING", 45.0, 1500.0, 8.5)));
    }

    @Test
    void telemetryWithoutReportAndSimulationOffReturnsNoneSource() {
        when(deviceService.getById(1L)).thenReturn(device());

        DeviceTelemetry t = deviceDataService.getTelemetry(1L);

        assertEquals("NONE", t.source());
        assertNull(t.temperature());
        assertNull(t.rpm());
        assertNull(t.current());
        assertNull(t.oee());
    }

    @Test
    void historyMarksTruncationWhenMorePointsExist() {
        when(deviceService.getById(1L)).thenReturn(device());
        when(telemetryMapper.selectCount(any())).thenReturn(200_000L);
        DeviceTelemetryRecord record = new DeviceTelemetryRecord();
        record.setReportedAt(LocalDateTime.of(2026, 11, 6, 10, 0));
        record.setStatus("RUNNING");
        when(telemetryMapper.selectList(any())).thenReturn(List.of(record));

        TelemetryHistory history = deviceDataService.historyResult(1L, null, null, 1000);

        assertEquals(200_000L, history.total());
        assertEquals(true, history.truncated());
        assertEquals(1, history.points().size());
    }

    @Test
    void historyReturnsLatestPointsInAscendingOrder() {
        when(deviceService.getById(1L)).thenReturn(device());
        DeviceTelemetryRecord newer = new DeviceTelemetryRecord();
        newer.setReportedAt(LocalDateTime.of(2026, 11, 6, 11, 0));
        newer.setStatus("RUNNING");
        DeviceTelemetryRecord older = new DeviceTelemetryRecord();
        older.setReportedAt(LocalDateTime.of(2026, 11, 6, 10, 0));
        older.setStatus("RUNNING");
        when(telemetryMapper.selectList(any())).thenReturn(List.of(newer, older));

        List<DeviceTelemetryPoint> points = deviceDataService.history(1L, null, null, 500);

        assertEquals(LocalDateTime.of(2026, 11, 6, 10, 0), points.get(0).reportedAt());
        assertEquals(LocalDateTime.of(2026, 11, 6, 11, 0), points.get(1).reportedAt());
    }
}
