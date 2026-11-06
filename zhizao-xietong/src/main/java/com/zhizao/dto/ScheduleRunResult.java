package com.zhizao.dto;

import com.zhizao.entity.ScheduleEntry;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 一次排程运行的结果摘要。
 *
 * @param runAt                 本次运行时间
 * @param scheduledWorkOrders   成功排入的工单数
 * @param unscheduledWorkOrders 无法排入的工单数
 * @param scheduled             已排入的工序明细
 * @param unscheduled           无法排入的原因明细
 * @author Caspian
 */
public record ScheduleRunResult(LocalDateTime runAt, int scheduledWorkOrders, int unscheduledWorkOrders,
                                List<ScheduleEntry> scheduled, List<ScheduleEntry> unscheduled) {
}
