package com.travelmapper.service.ai;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class AiCancelService {

    private final StringRedisTemplate redis;

    public AiCancelService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public void cancel(Long userId, String requestId) {
        if (requestId == null || requestId.isBlank()) {
            return;
        }
        if (Boolean.TRUE.equals(redis.hasKey("quota:req:" + userId + ":" + requestId))) {
            redis.opsForValue().set("cancel:" + userId + ":" + requestId,
                    "1", Duration.ofMinutes(5));
        }
        // Unknown or other users' request ids also return success (idempotent, no leak).
    }
}
