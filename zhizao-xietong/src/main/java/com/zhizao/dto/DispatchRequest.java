package com.zhizao.dto;

/**
 * 下达工单请求。
 *
 * @param processId 工艺版本 ID，必填且必须为已发布工艺
 * @param deviceId  指定设备主键，可为空；为空时取计划上的所需设备
 * @param workshop  车间，可为空；为空时缺省「一车间」
 * @author Caspian
 */
public record DispatchRequest(Long processId, Long deviceId, String workshop) {
}
