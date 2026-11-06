package com.zhizao.dto.ai;

import java.util.List;
import java.util.Map;

/**
 * 只读查询结果。SQL 一并返回，便于管理员核对模型生成了什么。
 *
 * @param sql        实际执行的只读 SQL
 * @param columns    列名
 * @param rows       行数据
 * @param rowCount   返回行数
 * @param durationMs 执行耗时毫秒
 * @author Caspian
 */
public record AiQueryResult(String sql,
                            List<String> columns,
                            List<Map<String, Object>> rows,
                            int rowCount,
                            long durationMs) {
}
