package com.lingsql;

import com.lingsql.config.LingSqlProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * LingSQL Engine 启动类。
 *
 * @author 余炘洋
 * @date 2026-01-22
 */
@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
@EntityScan(basePackages = "com.lingsql")
@EnableJpaRepositories(basePackages = "com.lingsql")
@EnableConfigurationProperties(LingSqlProperties.class)
@EnableScheduling
public class LingSqlApplication {

    /**
     * 应用启动入口。
     *
     * @param args 启动参数
     * @author 余炘洋
     * @date 2026-01-22
     */
    public static void main(String[] args) {
        SpringApplication.run(LingSqlApplication.class, args);
        System.out.println("🚀 LingSQL 语数引擎启动成功！");
    }
}
