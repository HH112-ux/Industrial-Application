package com.zhizao.dto;

import java.time.LocalDateTime;

/**
 * 设备实时遥测：数值来自设备上报或显式开启的模拟数据，`source` 标明来源。
 *
 * @author Caspian
 */
public record DeviceTelemetry(String deviceNo, String deviceName, String status,
                              double temperature, int rpm, double current, double oee,
                              LocalDateTime timestamp, String source) {
}
