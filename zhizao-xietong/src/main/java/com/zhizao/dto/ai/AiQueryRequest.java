package com.zhizao.dto.ai;

/**
 * 生产数据问答请求，仅 ADMIN 可用。
 *
 * @param question 自然语言问题
 * @author Caspian
 */
public record AiQueryRequest(String question) {
}
