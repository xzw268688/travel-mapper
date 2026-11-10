package com.travelmapper.common.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 在线用户登记：Redis Hash 保存客户端信息，ZSet 保存最近活跃时间。
 * 5 分钟内有请求视为在线；登记用 60 秒节流，避免每请求写 Redis。
 */
@Slf4j
@Service
public class OnlineUserRegistry {

    public static final Duration WINDOW = Duration.ofMinutes(5);
    private static final Duration TOUCH_THROTTLE = Duration.ofSeconds(60);
    private static final String ZSET_KEY = "online:users";
    private static final int USER_AGENT_MAX_LENGTH = 255;

    private final StringRedisTemplate redis;

    public OnlineUserRegistry(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public void touch(RequestIdentity identity, String ip, String userAgent) {
        if (identity == null || !identity.isAuthenticated()) {
            return;
        }
        Long userId = identity.userId();
        try {
            Boolean fresh = redis.opsForValue()
                    .setIfAbsent(touchKey(userId), "1", TOUCH_THROTTLE);
            if (!Boolean.TRUE.equals(fresh)) {
                return;
            }
            long now = System.currentTimeMillis() / 1000;
            Map<String, String> fields = new LinkedHashMap<>();
            if (identity.role() != null) {
                fields.put("role", identity.role());
            }
            if (ip != null && !ip.isBlank()) {
                fields.put("ip", ip);
            }
            if (userAgent != null && !userAgent.isBlank()) {
                fields.put("userAgent", truncate(userAgent));
            }
            fields.put("lastSeen", String.valueOf(now));
            redis.opsForHash().putAll(userKey(userId), fields);
            redis.expire(userKey(userId), WINDOW);
            redis.opsForZSet().add(ZSET_KEY, String.valueOf(userId), now);
        } catch (Exception ex) {
            log.debug("online registry touch failed", ex);
        }
    }

    public List<OnlineUser> list() {
        long now = System.currentTimeMillis() / 1000;
        long cutoff = now - WINDOW.getSeconds();
        try {
            redis.opsForZSet().removeRangeByScore(ZSET_KEY, 0, cutoff);
            Set<String> members = redis.opsForZSet().rangeByScore(ZSET_KEY, cutoff, now);
            if (members == null || members.isEmpty()) {
                return List.of();
            }
            List<OnlineUser> result = new ArrayList<>();
            for (String member : members) {
                Map<Object, Object> fields = redis.opsForHash().entries(userKey(member));
                if (fields.isEmpty()) {
                    redis.opsForZSet().remove(ZSET_KEY, member);
                    continue;
                }
                long userId = Long.parseLong(member);
                result.add(new OnlineUser(
                        userId,
                        asString(fields.get("role")),
                        asString(fields.get("ip")),
                        asString(fields.get("userAgent")),
                        parseLong(fields.get("lastSeen"), cutoff)));
            }
            result.sort(Comparator.comparingLong(OnlineUser::lastSeenEpoch).reversed());
            return result;
        } catch (Exception ex) {
            log.warn("online registry list failed", ex);
            return List.of();
        }
    }

    public void remove(Long userId) {
        try {
            redis.delete(userKey(userId));
            redis.opsForZSet().remove(ZSET_KEY, String.valueOf(userId));
        } catch (Exception ex) {
            log.debug("online registry remove failed", ex);
        }
    }

    private String touchKey(Long userId) {
        return "online:touch:" + userId;
    }

    private String userKey(Long userId) {
        return "online:user:" + userId;
    }

    private String userKey(String userId) {
        return "online:user:" + userId;
    }

    private String truncate(String value) {
        String trimmed = value.trim();
        return trimmed.length() <= USER_AGENT_MAX_LENGTH
                ? trimmed : trimmed.substring(0, USER_AGENT_MAX_LENGTH);
    }

    private String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private long parseLong(Object value, long fallback) {
        try {
            return value == null ? fallback : Long.parseLong(value.toString());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    public record OnlineUser(Long userId, String role, String ip, String userAgent, long lastSeenEpoch) {
    }
}