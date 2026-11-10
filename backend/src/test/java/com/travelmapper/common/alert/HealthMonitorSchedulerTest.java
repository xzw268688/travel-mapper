package com.travelmapper.common.alert;

import com.travelmapper.service.storage.StorageConfig;
import com.travelmapper.service.storage.StorageProviderHolder;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HealthMonitorSchedulerTest {

    @Test
    void alertsOnOutageOnceAndOnRecovery() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        RedisConnectionFactory factory = mock(RedisConnectionFactory.class);
        RedisConnection connection = mock(RedisConnection.class);
        StorageProviderHolder storage = mock(StorageProviderHolder.class);
        AlertNotifier notifier = mock(AlertNotifier.class);

        when(redis.getConnectionFactory()).thenReturn(factory);
        when(factory.getConnection()).thenReturn(connection);
        when(connection.ping()).thenReturn("PONG");
        when(storage.loadConfig()).thenReturn(new StorageConfig(
                "", "", "", "", "", "", true, ""));
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class)))
                .thenThrow(new RuntimeException("db down"));

        HealthMonitorScheduler scheduler =
                new HealthMonitorScheduler(jdbcTemplate, redis, storage, notifier);

        scheduler.checkAll();
        scheduler.checkAll();
        verify(notifier, times(1)).notify(eq("health:数据库"), eq("数据库 不可用"),
                contains("db down"));

        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class))).thenReturn(1);
        scheduler.checkAll();
        verify(notifier, times(1)).notify(eq("health:数据库"), eq("数据库 已恢复"),
                anyString());
    }
}