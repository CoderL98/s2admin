package com.s2admin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * S2Admin 后端系统启动类
 * 通用后台管理系统后端服务
 */
@SpringBootApplication
@EnableJpaAuditing
@EnableScheduling
public class S2AdminApplication {

    public static void main(String[] args) {
        SpringApplication.run(S2AdminApplication.class, args);
    }
}
