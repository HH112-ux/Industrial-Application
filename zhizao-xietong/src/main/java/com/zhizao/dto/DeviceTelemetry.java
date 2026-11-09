package com.zhizao.dto;

import java.time.LocalDateTime;

/**
 * 设备实时遥测：数值来自设备上报或显式开启的模拟数据，`source` 标明来源。
 *
 * <p>没有任何数据时数值字段为 null（而不是 0），前端据此显示「无数据」，
 * 避免把占位 0 当作真实读数。</p>
 *
 * @author Caspian
 */
public record DeviceTelemetry(String deviceNo, String deviceName, String status,
                              Double temperature, Integer rpm, Double current, Double oee,
                              LocalDateTime timestamp, String source) {
}
