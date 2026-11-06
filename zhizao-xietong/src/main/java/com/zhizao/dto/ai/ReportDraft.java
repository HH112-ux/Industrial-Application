package com.zhizao.dto.ai;

import java.util.List;

/**
 * 报工表单草稿。服务端固定 {@code requiresConfirmation=true}，由人在现有报工接口确认提交。
 *
 * @param qty                  报工数量，缺失时为 null，不做猜测
 * @param qualifiedQty         合格数量，缺失时为 null
 * @param defectQty            不良数量，缺失时为 null
 * @param operator             操作工
 * @param remark               备注
 * @param missingFields        待补全字段
 * @param warnings             只读校验提示
 * @param requiresConfirmation 是否必须人工确认，服务端始终为 true
 * @author Caspian
 */
public record ReportDraft(Integer qty,
                          Integer qualifiedQty,
                          Integer defectQty,
                          String operator,
                          String remark,
                          List<String> missingFields,
                          List<String> warnings,
                          boolean requiresConfirmation) {
}
