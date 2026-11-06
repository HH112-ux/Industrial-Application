package com.zhizao.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhizao.common.BusinessException;
import com.zhizao.common.Result;
import com.zhizao.dto.DeviceGatewayReport;
import com.zhizao.dto.DeviceTelemetry;
import com.zhizao.gateway.MqttDeviceGateway;
import com.zhizao.security.DeviceSignatureVerifier;
import com.zhizao.service.DeviceDataService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 设备数据接入：提供实时遥测查询、设备上报与 MQTT 测试发布入口。
 *
 * @author Caspian
 */
@Tag(name = "设备数据接入")
@RestController
@RequestMapping("/api")
public class DeviceDataController {

    private final DeviceDataService deviceDataService;
    private final ObjectProvider<MqttDeviceGateway> mqttGateway;
    private final DeviceSignatureVerifier signatureVerifier;
    private final ObjectMapper objectMapper;

    /**
     * 构建设备数据接入控制器。
     *
     * @param deviceDataService 设备上报处理服务
     * @param mqttGateway       MQTT 网关，未启用时为空
     * @param signatureVerifier 设备签名校验组件
     * @param objectMapper      JSON 序列化组件
     */
    public DeviceDataController(DeviceDataService deviceDataService, ObjectProvider<MqttDeviceGateway> mqttGateway,
                                DeviceSignatureVerifier signatureVerifier, ObjectMapper objectMapper) {
        this.deviceDataService = deviceDataService;
        this.mqttGateway = mqttGateway;
        this.signatureVerifier = signatureVerifier;
        this.objectMapper = objectMapper;
    }

    /** 设备实时遥测（优先返回网关真实上报，无上报时返回模拟）。 */
    @GetMapping("/devices/{id}/telemetry")
    public Result<DeviceTelemetry> telemetry(@PathVariable Long id) {
        return Result.ok(deviceDataService.getTelemetry(id));
    }

    /**
     * 设备状态回传。鉴权开启时校验 `X-Device-Key` 与 `X-Device-Signature`，
     * 签名对象为原始请求体；`X-Idempotency-Key` 用于设备重发去重。
     *
     * @param rawBody        原始请求体，签名校验必须基于未解析的字节内容
     * @param deviceKey      设备凭证标识，鉴权开启时必填
     * @param signature      十六进制 HMAC-SHA256 签名，鉴权开启时必填
     * @param idempotencyKey 设备侧唯一键，可为空
     * @return 成功返回空响应体；重复上报同样返回成功且不重复写入
     * @throws com.zhizao.common.DeviceAuthException 签名错误返回 401，设备停用返回 403
     * @throws BusinessException                    请求体格式错误时抛出
     */
    @PostMapping("/integration/device-status")
    public Result<Void> pushStatus(@RequestBody String rawBody,
                                   @RequestHeader(value = "X-Device-Key", required = false) String deviceKey,
                                   @RequestHeader(value = "X-Device-Signature", required = false) String signature,
                                   @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey) {
        signatureVerifier.verifyRawPayload(deviceKey, signature, rawBody);
        DeviceGatewayReport report;
        try {
            report = objectMapper.readValue(rawBody, DeviceGatewayReport.class);
        } catch (Exception e) {
            throw new BusinessException("请求体格式错误");
        }
        deviceDataService.handleGatewayReport(report, idempotencyKey, "HTTP");
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
