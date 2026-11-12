package com.zhizao.integration;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhizao.dto.DeviceGatewayReport;
import com.zhizao.entity.DeviceTelemetryRecord;
import com.zhizao.mapper.DeviceTelemetryMapper;
import com.zhizao.service.DeviceDataService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 遥测落库的集成测试：在真实 MySQL 上验证唯一键按设备隔离。
 *
 * <p>此前 {@code uk_ingest_key(ingest_key)} 单列唯一，两台设备的自增 messageId
 * 相同就会互相撞键，后一条历史遥测被静默丢弃。该用例在修复前必然只落一条。</p>
 *
 * @author Caspian
 */
@SpringBootTest(properties = {
        "JWT_SECRET=integration-test-secret-key-not-for-production",
        "DB_PASSWORD=test",
        "app.bootstrap.admin-password=integration-test-admin-pass",
        // 本用例依赖演示设备编号；演示种子默认关闭，测试内显式开启
        "app.seed.demo-enabled=true"
})
@Testcontainers(disabledWithoutDocker = true)
class TelemetryIngestIntegrationTest {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    @Autowired
    private DeviceDataService deviceDataService;
    @Autowired
    private DeviceTelemetryMapper telemetryMapper;

    @Test
    void sameIngestKeyFromDifferentDevicesBothPersisted() {
        String ingestKey = "msg-" + System.nanoTime();

        deviceDataService.handleGatewayReport(new DeviceGatewayReport(
                "EQ-202609-0001", "RUNNING", 45.0, 1500.0, 8.5,
                null, null, null, null, ingestKey), ingestKey, "HTTP");
        deviceDataService.handleGatewayReport(new DeviceGatewayReport(
                "EQ-202609-0002", "RUNNING", 46.0, 1600.0, 9.0,
                null, null, null, null, ingestKey), ingestKey, "HTTP");

        List<DeviceTelemetryRecord> rows = telemetryMapper.selectList(
                new LambdaQueryWrapper<DeviceTelemetryRecord>()
                        .eq(DeviceTelemetryRecord::getIngestKey, ingestKey));
        assertEquals(2, rows.size(), "不同设备使用相同 ingestKey 都必须落库");
        assertEquals(2L, rows.stream().map(DeviceTelemetryRecord::getDeviceId).distinct().count());
    }

    @Test
    void duplicateIngestKeyFromSameDeviceIsStillDeduplicated() {
        String ingestKey = "dup-" + System.nanoTime();
        DeviceGatewayReport report = new DeviceGatewayReport(
                "EQ-202609-0003", "STOPPED", 30.0, 0.0, 0.0,
                null, null, null, null, ingestKey);

        deviceDataService.handleGatewayReport(report, ingestKey, "HTTP");
        deviceDataService.handleGatewayReport(report, ingestKey, "HTTP");

        assertEquals(1L, telemetryMapper.selectCount(new LambdaQueryWrapper<DeviceTelemetryRecord>()
                .eq(DeviceTelemetryRecord::getIngestKey, ingestKey)));
    }
}
