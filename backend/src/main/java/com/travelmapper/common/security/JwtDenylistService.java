package com.travelmapper.common.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 逐 Token 吊销名单：按 jti 写入 Redis，TTL 取该 Token 的剩余有效期。
 * Redis 不可用时降级为放行：吊销能力暂时失效，但不阻断全部已登录请求。
 */
@Slf4j
@Service
public class JwtDenylistService {

    private static final String KEY_PREFIX = "auth:jti:";

    private final StringRedisTemplate redis;

    public JwtDenylistService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public void revoke(String jti, long ttlSeconds) {
        if (jti == null || jti.isBlank() || ttlSeconds <= 0) {
            return;
        }
        try {
            redis.opsForValue().set(key(jti), "1", Duration.ofSeconds(ttlSeconds));
        } catch (RuntimeException ex) {
            log.warn("token denylist write failed, token cannot be revoked until expiry jti={}: {}",
                    jti, ex.toString());
        }
    }

    public boolean isRevoked(String jti) {
        if (jti == null || jti.isBlank()) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(redis.hasKey(key(jti)));
        } catch (RuntimeException ex) {
            log.warn("token denylist read failed, degrading to allow: {}", ex.toString());
            return false;
        }
    }

    private String key(String jti) {
        return KEY_PREFIX + jti;
    }
}