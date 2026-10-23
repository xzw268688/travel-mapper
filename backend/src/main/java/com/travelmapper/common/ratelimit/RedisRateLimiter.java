package com.travelmapper.common.ratelimit;

import com.travelmapper.common.api.BusinessException;
import com.travelmapper.common.api.ErrorCode;
import com.travelmapper.common.config.AppProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RedisRateLimiter {

    private static final DefaultRedisScript<Long> SCRIPT = new DefaultRedisScript<>(
            "local current = redis.call('INCR', KEYS[1]) "
                    + "if current == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end "
                    + "return current",
            Long.class);

    private final StringRedisTemplate redis;
    private final AppProperties properties;

    public RedisRateLimiter(StringRedisTemplate redis, AppProperties properties) {
        this.redis = redis;
        this.properties = properties;
    }

    public boolean tryAcquire(String key, long limit, long windowSeconds) {
        if (!properties.getRateLimit().isEnabled()) {
            return true;
        }
        Long count = redis.execute(SCRIPT, List.of(key), String.valueOf(windowSeconds));
        return count != null && count <= limit;
    }

    public void check(String key, long limit, long windowSeconds) {
        if (!tryAcquire(key, limit, windowSeconds)) {
            throw new BusinessException(ErrorCode.RATE_LIMITED);
        }
    }
}
