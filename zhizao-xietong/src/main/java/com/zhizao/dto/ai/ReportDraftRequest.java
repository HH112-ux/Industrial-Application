package com.zhizao.dto.ai;

/**
 * 自然语言报工解析请求。模型只抽取字段，不提交报工。
 *
 * @param workOrderId 工单主键，用于二次校验归属与剩余数量
 * @param text        自然语言描述
 * @author Caspian
 */
public record ReportDraftRequest(Long workOrderId, String text) {
}
