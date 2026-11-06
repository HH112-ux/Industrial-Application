package com.zhizao.dto.ai;

import com.zhizao.entity.WorkOrder;

/**
 * 自然语言报工的只读输入。
 *
 * @param order 当前工单
 * @param text  用户描述
 * @author Caspian
 */
public record ReportDraftContext(WorkOrder order, String text) {
}
