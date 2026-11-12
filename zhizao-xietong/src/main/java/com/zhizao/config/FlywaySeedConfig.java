package com.zhizao.config;

import org.flywaydb.core.api.Location;
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
 * 才把该目录追加进 locations。该开关用于空库一次性安装演示环境；已升级到 V32 的存量库
 * 不再回放 V2（后续迁移已改变表结构，回放会失败），保持关闭即可。</p>
 *
 * <p>历史上 {@code V2__seed.sql} 位于 {@code db/migration}，脚本内容未做任何修改。
 * 已执行过该版本的存量库通过 {@code spring.flyway.ignore-migration-patterns=*:missing}
 * 放行「已执行但当前 locations 中不存在」的版本；checksum、失败迁移与乱序迁移仍然校验，
 * 不是关闭全部校验。</p>
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
}
