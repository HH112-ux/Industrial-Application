package com.zhizao;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.zhizao.mapper")
public class ZhizaoApplication {

    public static void main(String[] args) {
        SpringApplication.run(ZhizaoApplication.class, args);
    }
}
