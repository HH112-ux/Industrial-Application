package com.zhizao.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhizao.common.BusinessException;
import com.zhizao.common.Result;
import com.zhizao.dto.DeviceGatewayReport;
import com.zhizao.dto.DeviceTelemetry;
import com.zhizao.dto.TelemetryHistory;
import com.zhizao.gateway.MqttDeviceGateway;
import com.zhizao.security.DeviceSignatureVerifier;
import com.zhizao.service.DeviceDataService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * 设备数据接入：提供实时遥测、遥测历史查询、设备上报与 MQTT 测试发布入口。
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
     * 查询设备遥测历史，默认最近 24 小时，按上报时间升序返回最近的点，最多 1000 条。
     *
     * @param id    设备主键
     * @param from  起始时间（ISO-8601），可为空
     * @param to    结束时间（ISO-8601），可为空
     * @param limit 返回条数上限，默认 500，超过 1000 按 1000 处理
     * @return 遥测采样与是否截断的标记
     * @throws com.zhizao.common.BadRequestException 起始时间晚于结束时间时返回 400
     * @throws com.zhizao.common.BusinessException   设备不存在时抛出
     */
    @GetMapping("/devices/{id}/telemetry/history")
    public Result<TelemetryHistory> telemetryHistory(
            @PathVariable Long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(required = false) Integer limit) {
        return Result.ok(deviceDataService.historyResult(id, from, to, limit));
    }

    /**
     * 设备状态回传。鉴权开启时校验 `X-Device-Key` 与 `X-Device-Signature`，
     * 签名对象为原始请求体，并按报文 `deviceNo` 校验凭证与设备的绑定关系；
     * `X-Idempotency-Key` 用于设备重发去重。
     *
     * @param rawBody        原始请求体，签名校验必须基于未解析的字节内容
     * @param deviceKey      设备凭证标识，鉴权开启时必填
     * @param signature      十六进制 HMAC-SHA256 签名，鉴权开启时必填
     * @param idempotencyKey 设备侧唯一键，可为空
     * @return 成功返回空响应体；重复上报同样返回成功且不重复写入
     * @throws com.zhizao.common.DeviceAuthException 签名错误返回 401，设备停用或凭证与设备编号不匹配返回 403
     * @throws BusinessException                    请求体格式错误时抛出
     */
    @PostMapping("/integration/device-status")
    public Result<Void> pushStatus(@RequestBody String rawBody,
                                   @RequestHeader(value = "X-Device-Key", required = false) String deviceKey,
                                   @RequestHeader(value = "X-Device-Signature", required = false) String signature,
                                   @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey) {
        DeviceGatewayReport report;
        try {
            report = objectMapper.readValue(rawBody, DeviceGatewayReport.class);
        } catch (Exception e) {
            throw new BusinessException("请求体格式错误");
        }
        // 验签对象始终是原始请求体；先解析只是为了取得 deviceNo 做凭证绑定校验
        signatureVerifier.verifyRawPayload(deviceKey, signature, rawBody, report.deviceNo());
        deviceDataService.handleGatewayReport(report, idempotencyKey, "HTTP");
        return Result.ok();
    }

    /** 演示：通过 MQTT 发布一条测试上报（需启用 MQTT 网关，仅管理员可用）。 */
    @PreAuthorize("hasRole('ADMIN')")
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
