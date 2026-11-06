package com.zhizao.ai;

import com.zhizao.config.AiProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 只读 SQL 白名单：只接受单条 SELECT，只允许访问 5 个只读视图，并强制行数上限。
 *
 * <p>校验通过后统一包一层子查询并追加 {@code LIMIT}，即使模型自带更大的 LIMIT 也会被截断。
 * 任何 DDL、DML、注释、多语句、非白名单表或系统库都会被拒绝。</p>
 *
 * @author Caspian
 */
@Component
public class ReadOnlySqlGuard {

    private static final Set<String> ALLOWED_VIEWS = Set.of(
            "v_work_order_summary", "v_device_status", "v_quality_summary", "v_plan_progress", "v_kit_status");

    private static final Set<String> SENSITIVE_TABLES = Set.of(
            "sys_user", "sys_audit_log", "sys_sequence", "eq_device", "eq_device_credential", "eq_telemetry",
            "eq_maintenance", "wo_work_order", "wo_report", "wo_quality", "wo_status_history", "pr_process",
            "pr_process_operation", "pr_bom", "po_plan", "po_schedule_entry", "mt_material",
            "ai_suggestion_log", "ai_query_audit");

    private static final Pattern BANNED = Pattern.compile(
            "\\b(insert|update|delete|drop|alter|create|truncate|replace|grant|revoke|call|execute|merge|"
                    + "load_file|outfile|dumpfile|sleep|benchmark|handler|prepare|deallocate)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern FROM_CLAUSE = Pattern.compile(
            "\\bfrom\\b(.*?)(\\bwhere\\b|\\bgroup\\b|\\border\\b|\\blimit\\b|\\bjoin\\b|\\bunion\\b|$)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private static final Pattern JOIN_TABLE = Pattern.compile(
            "\\bjoin\\s+([a-zA-Z0-9_]+)", Pattern.CASE_INSENSITIVE);

    private final AiProperties properties;

    /**
     * 构造白名单校验器。
     *
     * @param properties AI 配置，提供行数上限
     */
    public ReadOnlySqlGuard(AiProperties properties) {
        this.properties = properties;
    }

    /**
     * 校验并包装 SQL。
     *
     * @param candidate 模型生成的 SQL
     * @return 可直接执行的只读 SQL，带强制行数上限
     * @throws SqlRejectedException 未通过白名单校验时抛出
     */
    public String validate(String candidate) {
        if (!StringUtils.hasText(candidate)) {
            throw new SqlRejectedException("EMPTY_SQL");
        }
        String sql = candidate.trim();
        while (sql.endsWith(";")) {
            sql = sql.substring(0, sql.length() - 1).trim();
        }
        if (sql.contains(";")) {
            throw new SqlRejectedException("MULTI_STATEMENT");
        }
        String sanitized = sql.replaceAll("'(?:[^']|'')*'", "''");
        if (sanitized.contains("--") || sanitized.contains("/*") || sanitized.contains("*/")
                || sanitized.contains("#") || sanitized.contains("\"")) {
            throw new SqlRejectedException("COMMENT_OR_QUOTED_IDENTIFIER");
        }
        String lower = sanitized.toLowerCase(Locale.ROOT);
        if (BANNED.matcher(sanitized).find()) {
            throw new SqlRejectedException("WRITE_OR_DANGEROUS_KEYWORD");
        }
        if (!lower.startsWith("select")) {
            throw new SqlRejectedException("NOT_SELECT");
        }
        if (lower.contains("for update") || lower.contains("lock in share mode")) {
            throw new SqlRejectedException("LOCKING_READ");
        }
        for (String table : SENSITIVE_TABLES) {
            if (Pattern.compile("\\b" + table + "\\b", Pattern.CASE_INSENSITIVE).matcher(sanitized).find()) {
                throw new SqlRejectedException("SENSITIVE_TABLE");
            }
        }

        Set<String> tables = new LinkedHashSet<>();
        Matcher fromMatcher = FROM_CLAUSE.matcher(sanitized);
        while (fromMatcher.find()) {
            String clause = fromMatcher.group(1).trim();
            if (clause.startsWith("(")) {
                continue;
            }
            for (String part : clause.split(",")) {
                String name = part.trim().split("\\s+")[0];
                if (StringUtils.hasText(name)) {
                    tables.add(name);
                }
            }
        }
        Matcher joinMatcher = JOIN_TABLE.matcher(sanitized);
        while (joinMatcher.find()) {
            tables.add(joinMatcher.group(1));
        }
        if (tables.isEmpty()) {
            throw new SqlRejectedException("NO_TABLE");
        }
        for (String table : tables) {
            String normalized = table.replace("`", "");
            if (normalized.contains(".") || !ALLOWED_VIEWS.contains(normalized)) {
                throw new SqlRejectedException("TABLE_NOT_ALLOWED");
            }
        }

        int maxRows = Math.max(properties.getMaxRows(), 1);
        return "SELECT * FROM (\n" + sql + "\n) ai_readonly LIMIT " + maxRows;
    }
}
