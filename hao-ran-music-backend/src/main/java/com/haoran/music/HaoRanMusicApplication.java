package com.haoran.music;

import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;






@Slf4j
@SpringBootApplication(scanBasePackages = "com.haoran.music")
@MapperScan("com.haoran.music.mapper")
@EnableCaching
@EnableAsync
@EnableScheduling
public class HaoRanMusicApplication {

    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(HaoRanMusicApplication.class, args);
        Environment env = context.getEnvironment();
        String port = env.getProperty("server.port", "");
        String contextPath = env.getProperty("server.servlet.context-path", "");
        log.info("\n============================================");
        log.info("  HaoRan Music backend started");
        log.info("  Server port: {}", port);
        log.info("  API context path: {}", contextPath);
        log.info("============================================\n");
    }
}
