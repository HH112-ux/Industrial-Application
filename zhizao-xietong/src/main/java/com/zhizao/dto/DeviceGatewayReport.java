package com.zhizao.dto;

/**
 * 设备网关上报（MQTT / 集成层统一格式）。
 *
 * <p>设备鉴权开启时，`keyId`、`timestamp`、`nonce` 与 `signature` 必填；签名规范串见
 * {@link com.zhizao.security.DeviceSignatureVerifier#canonicalReport(DeviceGatewayReport)}。
 * `messageId` 用于设备重发去重，对应 REST 的 `X-Idempotency-Key`。</p>
 *
 * @author Caspian
 */
public record DeviceGatewayReport(
        String deviceNo,
        String status,
        Double temperature,
        Double rpm,
        Double current,
        String keyId,
        Long timestamp,
        String nonce,
        String signature,
        String messageId) {

    /**
     * 兼容未携带鉴权字段的旧上报格式，鉴权关闭时可直接使用。
     *
     * @param deviceNo    设备编号
     * @param status      设备状态
     * @param temperature 温度
     * @param rpm         转速
     * @param current     电流
     */
    public DeviceGatewayReport(String deviceNo, String status, Double temperature, Double rpm, Double current) {
        this(deviceNo, status, temperature, rpm, current, null, null, null, null, null);
    }
}
