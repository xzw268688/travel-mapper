package com.travelmapper.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travelmapper.repository.entity.User;
import com.travelmapper.repository.mapper.UserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Slf4j
@Service
public class UserStatusCache {

    private static final Duration TTL = Duration.ofSeconds(30);

    private final StringRedisTemplate redis;
    private final UserMapper userMapper;
    private final ObjectMapper objectMapper;

    public UserStatusCache(StringRedisTemplate redis, UserMapper userMapper, ObjectMapper objectMapper) {
        this.redis = redis;
        this.userMapper = userMapper;
        this.objectMapper = objectMapper;
    }

    public UserAuthSnapshot get(Long userId) {
        String key = key(userId);
        try {
            String cached = redis.opsForValue().get(key);
            if (cached != null) {
                return objectMapper.readValue(cached, UserAuthSnapshot.class);
            }
        } catch (Exception ex) {
            log.debug("user status cache read failed, falling back to DB", ex);
        }

        User user = userMapper.selectByIdIncludingDeleted(userId);
        if (user == null) {
            return null;
        }
        UserAuthSnapshot snapshot = new UserAuthSnapshot(
                user.getId(), user.getRole().name(), user.getStatus().name(), user.getTokenVersion());
        try {
            redis.opsForValue().set(key, objectMapper.writeValueAsString(snapshot), TTL);
        } catch (Exception ex) {
            log.debug("user status cache write failed", ex);
        }
        return snapshot;
    }

    public void evict(Long userId) {
        try {
            redis.delete(key(userId));
        } catch (Exception ex) {
            log.debug("user status cache evict failed", ex);
        }
    }

    private String key(Long userId) {
        return "user:auth:" + userId;
    }
}
