package com.zhizao.dto;

/**
 * 工单状态聚合结果。
 *
 * @param status 工单状态编码
 * @param count  该状态下的工单数量
 * @author Caspian
 */
public record WorkOrderStatusCount(String status, long count) {
}
