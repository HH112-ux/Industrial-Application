package com.zhizao.service;

import com.zhizao.common.BusinessException;
import com.zhizao.dto.DeviceGatewayReport;
import com.zhizao.dto.DeviceTelemetry;
import com.zhizao.entity.Device;
import com.zhizao.mapper.DeviceMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
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
    private DeviceIdempotencyGuard idempotencyGuard;

    private DeviceDataService deviceDataService;

    /**
     * 构造被测服务，默认放行所有幂等键。
     */
    @BeforeEach
    void setUp() {
        deviceDataService = new DeviceDataService(deviceMapper, idempotencyGuard);
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
        when(deviceMapper.selectById(1L)).thenReturn(device());

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
    }

    @Test
    void handleReportUnknownDeviceThrows() {
        when(deviceMapper.selectOne(any())).thenReturn(null);

        assertThrows(BusinessException.class, () -> deviceDataService.handleGatewayReport(
                new DeviceGatewayReport("EQ-999", "RUNNING", 45.0, 1500.0, 8.5)));
    }

    @Test
    void telemetryWithoutReportAndSimulationOffReturnsNoneSource() {
        when(deviceMapper.selectById(1L)).thenReturn(device());

        DeviceTelemetry t = deviceDataService.getTelemetry(1L);

        assertEquals("NONE", t.source());
        assertEquals(0.0, t.temperature(), 0.01);
    }
}
