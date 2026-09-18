package com.zhizao.dto;

import java.time.LocalDateTime;

/**
 * 设备实时遥测（模拟）。
 */
public record DeviceTelemetry(String deviceNo, String deviceName, String status,
                              double temperature, int rpm, double current, double oee,
                              LocalDateTime timestamp) {
}
