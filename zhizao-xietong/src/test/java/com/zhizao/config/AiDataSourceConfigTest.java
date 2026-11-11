package com.zhizao.config;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.zhizao.ai.AiQueryExecutor;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AI 数据源装配：未配置独立只读库时必须回落主库并输出明确 WARN。
 *
 * @author Caspian
 */
class AiDataSourceConfigTest {

    @Test
    void missingUrlFallsBackToPrimaryWithExplicitWarning() {
        AiProperties properties = new AiProperties();
        Logger logger = (Logger) LoggerFactory.getLogger(AiDataSourceConfig.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            AiQueryExecutor executor = new AiDataSourceConfig().aiQueryExecutor(properties,
                    new DriverManagerDataSource());

            assertFalse(executor.isDedicatedReadOnlyAccount());
            assertTrue(appender.list.stream().anyMatch(event -> event.getFormattedMessage()
                    .contains("AI 查询未使用独立只读账号，隔离依赖正则与只读事务")));
        } finally {
            logger.detachAppender(appender);
        }
    }
}
