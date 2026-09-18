package com.zhizao.controller;

import com.zhizao.common.BusinessException;
import com.zhizao.common.Result;
import com.zhizao.dto.DeviceGatewayReport;
import com.zhizao.dto.DeviceStatusPushRequest;
import com.zhizao.dto.DeviceTelemetry;
import com.zhizao.gateway.MqttDeviceGateway;
import com.zhizao.service.DeviceDataService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "设备数据接入")
@RestController
@RequestMapping("/api")
public class DeviceDataController {

    private final DeviceDataService deviceDataService;
    private final ObjectProvider<MqttDeviceGateway> mqttGateway;

    public DeviceDataController(DeviceDataService deviceDataService, ObjectProvider<MqttDeviceGateway> mqttGateway) {
        this.deviceDataService = deviceDataService;
        this.mqttGateway = mqttGateway;
    }

    /** 设备实时遥测（优先返回网关真实上报，无上报时返回模拟）。 */
    @GetMapping("/devices/{id}/telemetry")
    public Result<DeviceTelemetry> telemetry(@PathVariable Long id) {
        return Result.ok(deviceDataService.getTelemetry(id));
    }

    /** 设备状态回传（REST 模拟推送）。 */
    @PostMapping("/integration/device-status")
    public Result<Void> pushStatus(@RequestBody DeviceStatusPushRequest request) {
        deviceDataService.pushStatus(request);
        return Result.ok();
    }

    /** 演示：通过 MQTT 发布一条测试上报（需启用 MQTT 网关）。 */
    @PostMapping("/integration/mqtt/publish")
    public Result<Void> publishMqtt(@RequestBody DeviceGatewayReport report) {
        MqttDeviceGateway gateway = mqttGateway.getIfAvailable();
        if (gateway == null) {
            throw new BusinessException("MQTT 网关未启用（请设置 mqtt.enabled=true 并启动 Broker）");
        }
        gateway.publishTest(report);
        return Result.ok();
    }
}
