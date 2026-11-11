package com.zhizao.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 排程明细视图：在排程结果之上补齐工单与设备的人可读标识，供前端展示时间轴与落点依据。
 *
 * <p>排程结果表只保存主键，前端如果自行反查就要多发请求，且容易在跨车间场景拼错数据。
 * 这里统一由服务端按当前用户的数据域补全，字段全部来自已存在的单据数据，不生成任何兜底值：
 * 工单或设备已被删除时保持 null，前端按主键展示。</p>
 *
 * @param id            排程记录主键
 * @param workOrderId   工单主键
 * @param orderNo       工单号
 * @param planId        计划主键
 * @param productCode   产品编号
 * @param productName   产品名称
 * @param workshop      工单车间
 * @param operationSeq  工序顺序
 * @param operationName 工序名称
 * @param deviceType    设备类型
 * @param deviceId      设备主键
 * @param deviceNo      设备编号
 * @param deviceName    设备名称
 * @param deviceWorkshop 设备所属车间；与工单车间不同表示跨车间落位
 * @param startTime     占用开始时间
 * @param endTime       占用结束时间
 * @param durationHours 占用工时（含换型）
 * @param hoursSource   工时来源：{@code STANDARD} / {@code ESTIMATED}；无法排入时为 null
 * @param queueRank     排产队列位置
 * @param rankBasis     队列位置依据，如交期与优先级
 * @param status        排程状态
 * @param conflictCode  无法排入时的业务码
 * @param message       落位说明或无法排入原因
 * @param occupied      是否为占用块：true 时表示该时段被数据域外工单占用，除设备与起止时间外
 *                      一律为 null，不携带任何工单内容
 * @author Caspian
 */
public record ScheduleEntryView(Long id, Long workOrderId, String orderNo, Long planId,
                                String productCode, String productName, String workshop,
                                Integer operationSeq, String operationName, String deviceType,
                                Long deviceId, String deviceNo, String deviceName, String deviceWorkshop,
                                LocalDateTime startTime, LocalDateTime endTime,
                                BigDecimal durationHours, String hoursSource,
                                Integer queueRank, String rankBasis, String status,
                                String conflictCode, String message, boolean occupied) {
}
