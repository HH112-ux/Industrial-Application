package com.zhizao.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * <p>已知限制：回落主数据源时，如果恢复语句本身失败，连接可能带着只读会话状态回到连接池。
 * 这里只记录告警，不做关闭或驱逐：连接池无法可靠验证会话状态，强行清理可能误伤正常连接。
 * 生产环境应配置独立只读账号，从源头上避免共享连接的会话状态污染。</p>
 *
 * @author Caspian
 */
public class AiQueryExecutor {

    private static final Logger log = LoggerFactory.getLogger(AiQueryExecutor.class);

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
                    restoreReadWrite(con, sql);
                }
            }
        });
    }

    /**
     * 恢复共享连接的读写状态。
     *
     * <p>恢复失败只告警、不抛出：此处已在 {@code finally} 中，抛出会覆盖查询本身的异常，
     * 调用方将看不到真正的失败原因。恢复失败的连接可能以只读状态回到连接池，
     * 当前没有可靠手段验证或精确驱逐该连接，因此不做额外清理。</p>
     *
     * @param con 连接
     * @param sql 本次查询 SQL，仅用于告警定位
     */
    private void restoreReadWrite(Connection con, String sql) {
        try {
            execute(con, READ_WRITE);
        } catch (SQLException ex) {
            log.warn("AI 查询恢复 READ WRITE 失败，连接可能以只读状态回池，"
                    + "建议配置独立只读数据源。sql={}", sql, ex);
        }
    }

    private void execute(Connection con, String sql) throws SQLException {
        try (Statement statement = con.createStatement()) {
            statement.execute(sql);
        }
    }
}
