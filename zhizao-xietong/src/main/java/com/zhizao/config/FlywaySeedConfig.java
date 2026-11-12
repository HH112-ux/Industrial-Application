package com.zhizao.config;

import org.flywaydb.core.api.Location;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.flywaydb.core.api.pattern.ValidatePattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.flyway.FlywayConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * 演示种子数据的可选加载配置。
 *
 * <p>演示种子脚本位于 {@code classpath:db/seed}，默认不在 Flyway locations 中，
 * 空库按默认配置启动不会写入任何演示设备、物料、工艺或计划。仅当
 * {@code app.seed.demo-enabled=true}（环境变量 {@code APP_SEED_DEMO_ENABLED}）时，
 * 才把该目录追加进 locations。该开关用于空库一次性安装演示环境；已升级到最新版本的存量库
 * 不再回放 V2（后续迁移已改变表结构，回放会失败），保持关闭即可。</p>
 *
 * <p>历史上 {@code V2__seed.sql} 位于 {@code db/migration}，脚本内容未做任何修改。
 * 已执行过该版本的存量库需要放行「已执行但当前 locations 中不存在」的版本，
 * 因此在种子关闭时追加 {@code *:missing} 校验模式。该模式是全局状态匹配，
 * 副作用是：如果将来有人误删或漏拷一个已执行过的迁移文件，Flyway 也会静默通过校验，
 * 而不是报错。升级巡检时应人工核对 {@code flyway_schema_history} 与
 * {@code db/migration} 文件清单，不能仅凭启动成功判断迁移完整。
 * checksum、失败迁移与乱序迁移仍然校验，不是关闭全部校验。</p>
 *
 * <p>种子开启时 V2 已在 locations 中，不再需要 {@code *:missing}，保持严格校验。</p>
 *
 * @author Caspian
 */
@Configuration
public class FlywaySeedConfig {

    private static final String SEED_LOCATION = "classpath:db/seed";

    /**
     * 按开关把演示种子目录追加到 Flyway locations。
     *
     * @param demoSeedEnabled 是否启用演示种子数据
     * @return Flyway 配置定制器
     */
    @Bean
    public FlywayConfigurationCustomizer demoSeedLocationCustomizer(
            @Value("${app.seed.demo-enabled:false}") boolean demoSeedEnabled) {
        return configuration -> {
            if (!demoSeedEnabled) {
                addIgnorePattern(configuration, "*:missing");
                return;
            }
            List<String> locations = new ArrayList<>();
            for (Location location : configuration.getLocations()) {
                locations.add(location.getDescriptor());
            }
            if (!locations.contains(SEED_LOCATION)) {
                locations.add(SEED_LOCATION);
            }
            configuration.locations(locations.toArray(new String[0]));
        };
    }

    /**
     * 追加一个 Flyway 校验忽略模式，保留已有模式。
     *
     * @param configuration Flyway 配置
     * @param pattern       待追加的校验模式，例如 {@code *:missing}
     */
    private static void addIgnorePattern(FluentConfiguration configuration, String pattern) {
        List<String> patterns = new ArrayList<>();
        for (ValidatePattern existing : configuration.getIgnoreMigrationPatterns()) {
            patterns.add(existing.toString());
        }
        if (!patterns.contains(pattern)) {
            patterns.add(pattern);
        }
        configuration.ignoreMigrationPatterns(patterns.toArray(new String[0]));
    }
}
