package com.zhizao.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.zhizao.ai.AiQueryExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;

/**
 * AI 查询执行器装配：优先使用独立只读数据源，未配置时回落主数据源。
 *
 * <p>回落路径不会假装已隔离：启动时明确输出 WARN，提示隔离只依赖 SQL 正则白名单与
 * 只读事务，建议在生产环境配置独立只读账号。</p>
 *
 * @author Caspian
 */
@Configuration
public class AiDataSourceConfig {

    private static final Logger log = LoggerFactory.getLogger(AiDataSourceConfig.class);

    /** 未配置独立只读数据源时的启动告警，测试会断言该文案。 */
    static final String FALLBACK_WARNING = "AI 查询未使用独立只读账号，隔离依赖正则与只读事务";

    /**
     * 创建 AI 查询执行器。
     *
     * @param properties AI 配置
     * @param dataSource 主数据源，仅在未配置独立只读库时使用
     * @return 查询执行器
     */
    @Bean
    public AiQueryExecutor aiQueryExecutor(AiProperties properties, DataSource dataSource) {
        AiProperties.Datasource datasource = properties.getDatasource();
        if (datasource != null && StringUtils.hasText(datasource.getUrl())) {
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(datasource.getUrl());
            config.setUsername(datasource.getUsername());
            config.setPassword(datasource.getPassword());
            config.setReadOnly(true);
            config.setMaximumPoolSize(2);
            config.setPoolName("ai-readonly");
            log.info("AI 查询使用独立只读数据源");
            return new AiQueryExecutor(new HikariDataSource(config), true);
        }
        log.warn(FALLBACK_WARNING);
        return new AiQueryExecutor(dataSource, false);
    }
}
