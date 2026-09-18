package com.zhizao.dto;

/**
 * 设备状态回传请求（模拟工业设备推送）。
 */
public record DeviceStatusPushRequest(String deviceNo, String status, String reportedAt) {
}
