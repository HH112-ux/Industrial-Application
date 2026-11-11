package com.zhizao.dto;

/**
 * 确认入库请求。
 *
 * <p>批次号可不传，留空时沿用生成待入库单时自动生成的「工单号-日期」。</p>
 *
 * @param batch 批次号覆盖值，最长 64 字
 * @author Caspian
 */
public record InboundConfirmRequest(String batch) {
}
