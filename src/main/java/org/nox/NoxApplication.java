package org.nox;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Nox 应用主入口类。
 * Spring Boot 应用的启动类，负责初始化 Spring 上下文并启动内嵌 Web 服务器。
 */
@SpringBootApplication
public class NoxApplication {

    /**
     * 程序入口方法。
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        // 运行 Spring Boot 应用，传入当前类作为配置源
        SpringApplication.run(NoxApplication.class, args);
    }

}
