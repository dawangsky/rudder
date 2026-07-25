package com.rudder.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

/**
 * Rudder 控制面入口。
 * <p>
 * 职责：工作区 / Issue / Chat / Agent / Task 队列与状态；不执行 LLM，不接触用户代码与 API Key。
 * <p>
 * 脚手架阶段暂时排除 DataSource / Redis 自动配置，避免未填写凭据时无法启动健康检查。
 * 接入 MySQL/Redis（任务 1.5）后将移除 exclude 并启用 persistence。
 */
@SpringBootApplication(exclude = {
        DataSourceAutoConfiguration.class,
        RedisAutoConfiguration.class
})
public class RudderServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(RudderServerApplication.class, args);
    }
}
