package com.zhizao.ai;

import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AI 查询执行器的恢复失败用例：恢复语句本身报错时不得覆盖原始查询异常。
 *
 * @author Caspian
 */
class AiQueryExecutorTest {

    @Test
    void restoreFailureDoesNotHideOriginalQueryError() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        PreparedStatement prepared = mock(PreparedStatement.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);
        when(connection.prepareStatement(anyString())).thenReturn(prepared);
        when(statement.execute(anyString())).thenReturn(true)
                .thenThrow(new SQLException("restore failed"));
        when(prepared.executeQuery()).thenThrow(new SQLException("query failed"));
        AiQueryExecutor executor = new AiQueryExecutor(dataSource, false);

        RuntimeException error = assertThrows(RuntimeException.class,
                () -> executor.query("SELECT 1", 3, 10));

        assertTrue(error.getMessage().contains("query failed"),
                "恢复失败不应覆盖原始查询异常，实际为：" + error.getMessage());
        verify(statement).execute("SET SESSION TRANSACTION READ ONLY");
        verify(statement).execute("SET SESSION TRANSACTION READ WRITE");
    }
}
