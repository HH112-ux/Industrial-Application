package com.zhizao;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 智造协同后端启动类：开启 Mapper 扫描与定时任务。
 *
 * @author Caspian
 */
@SpringBootApplication
@MapperScan("com.zhizao.mapper")
@EnableScheduling
public class ZhizaoApplication {

    /**
     * 启动 Spring Boot 应用。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(ZhizaoApplication.class, args);
    }
}
