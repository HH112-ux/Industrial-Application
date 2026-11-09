package com.zhizao.dto;

import java.util.List;

/**
 * 遥测历史查询结果。
 *
 * <p>为避免长区间查询只返回最早若干点、X 轴却覆盖整段时间，接口返回最近 {@code limit} 个采样点，
 * 并用 {@code truncated} 明确告诉调用方是否还有更早的数据被截断。</p>
 *
 * @param points    按上报时间升序排列的采样点
 * @param total     区间内实际存在的采样总数
 * @param truncated 是否因条数上限截断了更早的数据
 * @author Caspian
 */
public record TelemetryHistory(List<DeviceTelemetryPoint> points, long total, boolean truncated) {
}
