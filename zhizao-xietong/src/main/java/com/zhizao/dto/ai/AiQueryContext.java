package com.zhizao.dto.ai;

/**
 * Text-to-SQL 的模型输入。只包含问题文本，不包含任何业务表访问能力。
 *
 * @param question 自然语言问题
 * @author Caspian
 */
public record AiQueryContext(String question) {
}
