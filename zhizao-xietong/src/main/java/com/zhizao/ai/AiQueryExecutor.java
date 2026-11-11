package com.zhizao.ai;

import org.springframework.jdbc.core.ColumnMapRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 只读查询执行器：每条查询执行前把会话置为 {@code READ ONLY}。
 *
 * <p>配置了独立只读账号时使用专属连接池，会话保持只读；未配置时回落主数据源，
 * 查询结束后必须恢复 {@code READ WRITE}，避免只读状态污染业务连接。</p>
 *
 * @author Caspian
 */
public class AiQueryExecutor {

    private static final String READ_ONLY = "SET SESSION TRANSACTION READ ONLY";
    private static final String READ_WRITE = "SET SESSION TRANSACTION READ WRITE";

    private final JdbcTemplate jdbcTemplate;
    private final boolean dedicatedReadOnlyAccount;

    /**
     * 构造执行器。
     *
     * @param dataSource               查询数据源
     * @param dedicatedReadOnlyAccount true 表示数据源为 AI 专属只读账号
     */
    public AiQueryExecutor(DataSource dataSource, boolean dedicatedReadOnlyAccount) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        this.dedicatedReadOnlyAccount = dedicatedReadOnlyAccount;
    }

    /**
     * 是否使用独立只读账号。
     *
     * @return true 表示未回落主数据源
     */
    public boolean isDedicatedReadOnlyAccount() {
        return dedicatedReadOnlyAccount;
    }

    /**
     * 在只读会话中执行查询。
     *
     * @param sql            已通过正则白名单的 SQL
     * @param timeoutSeconds 查询超时秒数
     * @param maxRows        最大返回行数
     * @return 行数据
     */
    public List<Map<String, Object>> query(String sql, int timeoutSeconds, int maxRows) {
        return jdbcTemplate.execute((Connection con) -> {
            execute(con, READ_ONLY);
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setQueryTimeout(Math.max(timeoutSeconds, 1));
                ps.setMaxRows(Math.max(maxRows, 1));
                try (ResultSet rs = ps.executeQuery()) {
                    ColumnMapRowMapper mapper = new ColumnMapRowMapper();
                    List<Map<String, Object>> rows = new ArrayList<>();
                    int rowNum = 0;
                    while (rs.next()) {
                        rows.add(new LinkedHashMap<>(mapper.mapRow(rs, rowNum++)));
                    }
                    return rows;
                }
            } finally {
                if (!dedicatedReadOnlyAccount) {
                    execute(con, READ_WRITE);
                }
            }
        });
    }

    private void execute(Connection con, String sql) throws SQLException {
        try (Statement statement = con.createStatement()) {
            statement.execute(sql);
        }
    }
}
