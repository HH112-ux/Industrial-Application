package com.zhizao.dto;

/**
 * 报工请求。
 */
public record ReportRequest(Integer qty, Integer qualifiedQty, Integer defectQty, Long deviceId, String operator) {
}
