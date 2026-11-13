package com.zhizao.integration;

import com.zhizao.ai.AiQueryExecutor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * AI 只读会话集成测试：在真实 MySQL 上验证只读事务拒绝写入，且回落主库后连接已恢复可写。
 *
 * @author Caspian
 */
@SpringBootTest(properties = {
        "JWT_SECRET=integration-test-secret-key-not-for-production",
        "DB_PASSWORD=test",
        "app.bootstrap.admin-password=integration-test-admin-pass"
})
@Testcontainers
class AiReadOnlySessionIntegrationTest {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    @Autowired
    private DataSource dataSource;

    @Test
    void writeIsRejectedInReadOnlySessionAndSharedConnectionIsRestored() {
        AiQueryExecutor executor = new AiQueryExecutor(dataSource, false);

        assertEquals(1, executor.query("SELECT 1", 3, 10).size());
        assertThrows(RuntimeException.class,
                () -> executor.query("UPDATE sys_user SET nickname = nickname", 3, 10),
                "只读事务必须拒绝写语句");

        int updated = new JdbcTemplate(dataSource).update("UPDATE sys_user SET nickname = nickname WHERE 1 = 0");
        assertEquals(0, updated, "共享连接必须恢复为可写，否则会污染业务连接");
    }
}
