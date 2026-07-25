package com.rudder.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Rudder 控制面入口。
 * <p>
 * 职责：工作区 / Issue / Chat / Agent / Task 队列与状态；不执行 LLM，不接触用户代码与 API Key。
 * <p>
 * 本地开发启用 profile=local，并设置 rudder.persistence.enabled=true 以连接 MySQL/Redis。
 */
@SpringBootApplication
public class RudderServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(RudderServerApplication.class, args);
    }
}
