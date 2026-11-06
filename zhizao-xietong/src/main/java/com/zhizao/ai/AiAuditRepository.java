package com.zhizao.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * AI 留痕：只写建议摘要与查询审计，不保存 prompt 原文、响应正文或密钥。
 *
 * <p>审计写入失败只记录告警，不影响建议返回；AI 是旁路，不能因为留痕失败而放大故障。</p>
 *
 * @author Caspian
 */
@Component
public class AiAuditRepository {

    private static final Logger log = LoggerFactory.getLogger(AiAuditRepository.class);

    private final JdbcTemplate jdbcTemplate;

    /**
     * 构造留痕仓库。
     *
     * @param jdbcTemplate 数据库访问
     */
    public AiAuditRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 记录一次建议。
     *
     * @param scene       场景
     * @param username    发起人
     * @param targetRef   设备或工单标识
     * @param evidence    输入证据条数
     * @param confidence  置信度，可为空
     * @param summary     建议摘要
     * @param available   是否产出建议
     */
    public void logSuggestion(String scene, String username, String targetRef, int evidence,
                              Double confidence, String summary, boolean available) {
        try {
            jdbcTemplate.update(
                    "INSERT INTO ai_suggestion_log(scene, username, target_ref, evidence_count, confidence, "
                            + "suggestion_summary, available) VALUES (?, ?, ?, ?, ?, ?, ?)",
                    scene, username, targetRef, evidence, confidence, summary, available ? 1 : 0);
        } catch (DataAccessException e) {
            log.warn("Failed to persist AI suggestion log for scene {}", scene);
        }
    }

    /**
     * 记录一次只读查询。
     *
     * @param username 发起人
     * @param question 问题原文，仅用于计算摘要
     * @param sql      实际执行的 SQL
     * @param rowCount 返回行数
     * @param duration 耗时毫秒
     * @param success  是否成功
     * @param errorCode 失败类型
     */
    public void logQuery(String username, String question, String sql, int rowCount,
                         long duration, boolean success, String errorCode) {
        try {
            jdbcTemplate.update(
                    "INSERT INTO ai_query_audit(username, question_digest, sql_text, row_count, duration_ms, "
                            + "success, error_code) VALUES (?, ?, ?, ?, ?, ?, ?)",
                    username, digest(question), sql, rowCount, duration, success ? 1 : 0, errorCode);
        } catch (DataAccessException e) {
            log.warn("Failed to persist AI query audit");
        }
    }

    /**
     * 计算问题摘要，避免在审计表保存原文。
     *
     * @param text 原文
     * @return 十六进制 SHA-256
     */
    public static String digest(String text) {
        if (text == null) {
            return null;
        }
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            return null;
        }
    }
}
