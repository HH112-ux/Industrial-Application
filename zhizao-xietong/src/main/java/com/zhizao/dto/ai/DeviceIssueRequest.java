package com.zhizao.dto.ai;

import java.time.LocalDateTime;

/**
 * 设备异常归因请求。
 *
 * @param deviceId 设备主键
 * @param from     遥测窗口起点，可为空，缺省最近 24 小时
 * @param to       遥测窗口终点，可为空，缺省当前时间
 * @author Caspian
 */
public record DeviceIssueRequest(Long deviceId, LocalDateTime from, LocalDateTime to) {
}
