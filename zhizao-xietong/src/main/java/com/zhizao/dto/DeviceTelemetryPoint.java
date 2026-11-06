package com.zhizao.dto;

import java.time.LocalDateTime;

/**
 * 设备遥测历史点：按上报时间升序返回的单条采样。
 *
 * @author Caspian
 */
public record DeviceTelemetryPoint(LocalDateTime reportedAt,
                                   Double temperature,
                                   Integer rpm,
                                   Double current,
                                   String status,
                                   String source) {
}
