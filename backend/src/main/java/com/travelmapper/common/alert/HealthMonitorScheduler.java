package com.travelmapper.common.alert;

import com.travelmapper.service.storage.StorageProvider;
import com.travelmapper.service.storage.StorageProviderHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * 依赖健康检查：只在状态变化时告警，持续异常不重复提醒。
 */
@Slf4j
@Service
public class HealthMonitorScheduler {

    private static final String DATABASE = "数据库";
    private static final String REDIS = "Redis";
    private static final String STORAGE = "对象存储";

    private final JdbcTemplate jdbcTemplate;
    private final StringRedisTemplate redis;
    private final StorageProviderHolder storageHolder;
    private final AlertNotifier alertNotifier;
    private final Map<String, Boolean> states = new ConcurrentHashMap<>();

    public HealthMonitorScheduler(JdbcTemplate jdbcTemplate,
                                  StringRedisTemplate redis,
                                  StorageProviderHolder storageHolder,
                                  AlertNotifier alertNotifier) {
        this.jdbcTemplate = jdbcTemplate;
        this.redis = redis;
        this.storageHolder = storageHolder;
        this.alertNotifier = alertNotifier;
    }

    @Scheduled(cron = "${app.alert.health-cron:0 */5 * * * *}")
    public void checkAll() {
        check(DATABASE, this::checkDatabase);
        check(REDIS, this::checkRedis);
        checkStorage();
    }

    private void check(String name, Supplier<String> checker) {
        boolean healthy = true;
        String error = null;
        try {
            checker.get();
        } catch (Exception ex) {
            healthy = false;
            error = ex.getMessage();
        }
        applyState(name, healthy, error);
    }

    private void checkStorage() {
        if (!storageHolder.loadConfig().isConfigured()) {
            states.remove(STORAGE);
            return;
        }
        boolean healthy = true;
        String error = null;
        try {
            StorageProvider.ProbeResult probe = storageHolder.get().probe();
            healthy = probe.reachable();
            error = probe.message();
        } catch (Exception ex) {
            healthy = false;
            error = ex.getMessage();
        }
        applyState(STORAGE, healthy, error);
    }

    private void applyState(String name, boolean healthy, String error) {
        Boolean previous = states.put(name, healthy);
        if (!healthy && !Boolean.FALSE.equals(previous)) {
            log.warn("dependency unhealthy: {} {}", name, error);
            alertNotifier.notify("health:" + name, name + " 不可用",
                    name + " 健康检查失败：" + safeMessage(error));
        } else if (healthy && Boolean.FALSE.equals(previous)) {
            log.info("dependency recovered: {}", name);
            alertNotifier.notify("health:" + name, name + " 已恢复",
                    name + " 健康检查已恢复正常");
        }
    }

    private String checkDatabase() {
        Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
        return result == null ? "null" : result.toString();
    }

    private String checkRedis() {
        RedisConnection connection = redis.getConnectionFactory().getConnection();
        try {
            String pong = connection.ping();
            if (!"PONG".equalsIgnoreCase(pong)) {
                throw new IllegalStateException("ping=" + pong);
            }
            return pong;
        } finally {
            connection.close();
        }
    }

    private String safeMessage(String value) {
        if (value == null || value.isBlank()) {
            return "未知错误";
        }
        return value.length() > 300 ? value.substring(0, 300) : value;
    }
}