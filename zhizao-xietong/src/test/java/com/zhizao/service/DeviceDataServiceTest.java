package com.zhizao.service;

import com.zhizao.common.BusinessException;
import com.zhizao.dto.DeviceGatewayReport;
import com.zhizao.dto.DeviceTelemetry;
import com.zhizao.entity.Device;
import com.zhizao.mapper.DeviceMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeviceDataServiceTest {

    @Mock
    private DeviceMapper deviceMapper;

    @InjectMocks
    private DeviceDataService deviceDataService;

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
        verify(deviceMapper).updateById(any(Device.class));
    }

    @Test
    void handleReportUnknownDeviceThrows() {
        when(deviceMapper.selectOne(any())).thenReturn(null);

        assertThrows(BusinessException.class, () -> deviceDataService.handleGatewayReport(
                new DeviceGatewayReport("EQ-999", "RUNNING", 45.0, 1500.0, 8.5)));
    }
}
