package com.zhizao.dto;

/**
 * 设备网关上报（MQTT / 集成层统一格式）。
 */
public record DeviceGatewayReport(
        String deviceNo,
        String status,
        Double temperature,
        Double rpm,
        Double current) {
}
