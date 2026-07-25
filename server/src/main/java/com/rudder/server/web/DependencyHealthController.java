package com.rudder.server.web;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 依赖连通性检查：在启用持久化时探测 MySQL 与 Redis。
 */
@RestController
@ConditionalOnProperty(name = "rudder.persistence.enabled", havingValue = "true")
public class DependencyHealthController {

    private final JdbcTemplate jdbcTemplate;
    private final StringRedisTemplate redisTemplate;

    public DependencyHealthController(JdbcTemplate jdbcTemplate,
                                       ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
        this.jdbcTemplate = jdbcTemplate;
        this.redisTemplate = redisTemplateProvider.getIfAvailable();
    }

    @GetMapping("/api/health/deps")
    public Map<String, Object> deps() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("mysql", probeMysql());
        result.put("redis", probeRedis());
        return result;
    }

    private Map<String, Object> probeMysql() {
        try {
            Integer one = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            Integer tables = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name LIKE 'rb_%'",
                    Integer.class);
            return Map.of("ok", true, "select1", one, "rbTableCount", tables == null ? 0 : tables);
        } catch (Exception ex) {
            return Map.of("ok", false, "error", ex.getMessage());
        }
    }

    private Map<String, Object> probeRedis() {
        if (redisTemplate == null) {
            return Map.of("ok", false, "error", "StringRedisTemplate not available");
        }
        try {
            // execute 会正确归还连接，避免泄漏
            String pong = redisTemplate.execute((org.springframework.data.redis.connection.RedisConnection connection) -> {
                String reply = connection.ping();
                return reply == null ? "PONG" : reply;
            });
            return Map.of("ok", true, "ping", pong == null ? "PONG" : pong);
        } catch (Exception ex) {
            return Map.of("ok", false, "error", ex.getMessage());
        }
    }
}
