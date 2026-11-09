package com.travelmapper.service.monitor;

import com.travelmapper.common.api.BusinessException;
import com.travelmapper.common.api.ErrorCode;
import com.travelmapper.controller.admin.dto.MonitorDtos;
import com.travelmapper.service.audit.AuditLogService;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AdminCacheMonitorService {

    private static final int MAX_SCAN = 20_000;
    private static final int TTL_SAMPLE = 20;
    private static final int DELETE_BATCH = 500;
    private static final int DELETE_ROUNDS = 1000;
    private static final Pattern DB_KEYS = Pattern.compile("keys=(\\d+)");

    private static final List<Group> GROUPS = List.of(
            new Group("auth-code", "验证码", List.of("auth:code:"), true),
            new Group("login-fail", "登录失败", List.of("login:fail:"), true),
            new Group("rate-limit", "限流与防刷", List.of("rate:", "feedback:submit:",
                    "report:ip:", "report:anon:", "card:idem:"), true),
            new Group("idempotency", "幂等记录", List.of("idem:"), true),
            new Group("quota-dedupe", "AI 结果去重", List.of("quota:dedupe:"), true),
            new Group("search-cache", "搜索与攻略缓存", List.of("search:", "guides:",
                    "country-guide:v1:"), true),
            new Group("ai-cancel", "AI 取消", List.of("cancel:"), true),
            new Group("user-auth", "用户状态", List.of("user:auth:"), false),
            new Group("quota", "额度", List.of("quota:free:", "quota:daily:", "quota:pack:",
                    "quota:req:", "quota:deduct:"), false),
            new Group("lock", "分布式锁", List.of("lock:", "country-guide:lock:"), false));

    private final StringRedisTemplate redis;
    private final AuditLogService auditLogService;

    public AdminCacheMonitorService(StringRedisTemplate redis, AuditLogService auditLogService) {
        this.redis = redis;
        this.auditLogService = auditLogService;
    }

    public MonitorDtos.CacheMetrics metrics() {
        RedisConnection connection = redis.getConnectionFactory().getConnection();
        try {
            Properties memory = connection.info("memory");
            Properties stats = connection.info("stats");
            Properties clients = connection.info("clients");
            Properties keyspace = connection.info("keyspace");
            long hits = parseLong(stats.getProperty("keyspace_hits"));
            long misses = parseLong(stats.getProperty("keyspace_misses"));
            double hitRate = hits + misses == 0
                    ? 0 : Math.round(hits * 10000.0 / (hits + misses)) / 10000.0;
            MonitorDtos.CacheOverview overview = new MonitorDtos.CacheOverview(
                    memory.getProperty("used_memory_human", "—"),
                    memory.getProperty("maxmemory_human", "—"),
                    parseLong(clients.getProperty("connected_clients")),
                    hitRate,
                    hits,
                    misses,
                    dbKeys(keyspace.getProperty("db0")));
            List<MonitorDtos.CacheGroupView> groups = new ArrayList<>();
            for (Group group : GROUPS) {
                groups.add(stats(connection, group));
            }
            return new MonitorDtos.CacheMetrics(overview, groups);
        } finally {
            connection.close();
        }
    }

    public MonitorDtos.CacheClearResponse clear(Long adminId, String groupKey) {
        Group group = GROUPS.stream()
                .filter(item -> item.key().equals(groupKey))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.PARAM_INVALID, "未知缓存分组"));
        if (!group.clearable()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "该缓存分组不允许清理");
        }
        long deleted = 0;
        RedisConnection connection = redis.getConnectionFactory().getConnection();
        try {
            for (String prefix : group.prefixes()) {
                deleted += deletePrefix(connection, prefix);
            }
        } finally {
            connection.close();
        }
        auditLogService.record(adminId, "CACHE_CLEAR", "CACHE", null,
                Map.of("group", group.key(), "deleted", deleted), null);
        return new MonitorDtos.CacheClearResponse(group.key(), deleted);
    }

    private MonitorDtos.CacheGroupView stats(RedisConnection connection, Group group) {
        long count = 0;
        boolean truncated = false;
        List<Long> ttls = new ArrayList<>();
        outer:
        for (String prefix : group.prefixes()) {
            try (Cursor<byte[]> cursor = connection.scan(scanOptions(prefix))) {
                while (cursor.hasNext()) {
                    byte[] key = cursor.next();
                    if (count >= MAX_SCAN) {
                        truncated = true;
                        break outer;
                    }
                    count++;
                    if (ttls.size() < TTL_SAMPLE) {
                        Long ttl = connection.ttl(key);
                        if (ttl != null && ttl >= 0) {
                            ttls.add(ttl);
                        }
                    }
                }
            }
        }
        Long minTtl = ttls.stream().min(Long::compareTo).orElse(null);
        Long maxTtl = ttls.stream().max(Long::compareTo).orElse(null);
        return new MonitorDtos.CacheGroupView(group.key(), group.label(), count,
                minTtl, maxTtl, truncated, group.clearable());
    }

    private long deletePrefix(RedisConnection connection, String prefix) {
        long deleted = 0;
        for (int round = 0; round < DELETE_ROUNDS; round++) {
            List<byte[]> batch = new ArrayList<>();
            try (Cursor<byte[]> cursor = connection.scan(scanOptions(prefix))) {
                while (cursor.hasNext() && batch.size() < DELETE_BATCH) {
                    batch.add(cursor.next());
                }
            }
            if (batch.isEmpty()) {
                break;
            }
            Long removed = connection.del(batch.toArray(new byte[0][]));
            deleted += removed == null ? 0 : removed;
        }
        return deleted;
    }

    private ScanOptions scanOptions(String prefix) {
        return ScanOptions.scanOptions().match(prefix + "*").count(500).build();
    }

    private long dbKeys(String db0) {
        if (db0 == null) {
            return 0;
        }
        Matcher matcher = DB_KEYS.matcher(db0);
        return matcher.find() ? parseLong(matcher.group(1)) : 0;
    }

    private long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private record Group(String key, String label, List<String> prefixes, boolean clearable) {
    }
}