package com.zhizao.dto;

/**
 * 报工请求。
 *
 * <p>`idempotencyKey` 由客户端生成并在重试时复用；为空表示调用方不要求幂等，
 * 同一工单内相同键只接受一次报工。</p>
 *
 * @param qty            报工数量，必须大于 0
 * @param qualifiedQty   合格数量，不能为负
 * @param defectQty      不良数量，不能为负，且与合格数量之和等于报工数量
 * @param deviceId       报工设备主键，可为空
 * @param operator       操作人，会写入报工记录与状态历史
 * @param idempotencyKey 幂等键，最长 64 字符，可为空
 * @author Caspian
 */
public record ReportRequest(Integer qty, Integer qualifiedQty, Integer defectQty, Long deviceId,
                            String operator, String idempotencyKey) {
}
