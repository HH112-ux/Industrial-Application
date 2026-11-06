package com.zhizao.ai;

import com.zhizao.dto.ai.AiQueryContext;
import com.zhizao.dto.ai.DeviceIssueContext;
import com.zhizao.dto.ai.DeviceIssueSuggestion;
import com.zhizao.dto.ai.ReportDraft;
import com.zhizao.dto.ai.ReportDraftContext;

/**
 * AI 模型端口。实现只能生成建议，不得执行任何业务写操作。
 *
 * <p>该端口不接收数据库连接、Service 写方法或事务对象，从结构上隔离「模型直接改数据」。</p>
 *
 * @author Caspian
 */
public interface AiAssistant {

    /**
     * 生成设备异常归因建议。
     *
     * @param context 只读设备上下文
     * @return 建议，每条原因必须携带证据
     */
    DeviceIssueSuggestion suggestDeviceIssue(DeviceIssueContext context);

    /**
     * 从自然语言中抽取报工字段，只产出草稿。
     *
     * @param context 工单与用户描述
     * @return 报工草稿；缺失字段保持 null
     */
    ReportDraft draftReport(ReportDraftContext context);

    /**
     * 根据问题生成只读 SQL 候选语句。
     *
     * @param context 用户问题
     * @return SQL 文本，仍需通过只读白名单校验
     */
    String proposeSql(AiQueryContext context);
}
