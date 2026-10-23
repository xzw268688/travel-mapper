package com.travelmapper.service.auth;

import com.travelmapper.common.api.BusinessException;
import com.travelmapper.common.api.ErrorCode;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Locale;

@Service
public class LoginAttemptService {

    private static final int MAX_FAILURES = 5;
    private static final Duration WINDOW = Duration.ofMinutes(15);

    private final StringRedisTemplate redis;

    public LoginAttemptService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public void checkNotLocked(String account) {
        String value = redis.opsForValue().get(key(account));
        if (value != null) {
            try {
                if (Integer.parseInt(value) >= MAX_FAILURES) {
                    throw new BusinessException(ErrorCode.RATE_LIMITED, "登录失败次数过多，请 15 分钟后再试");
                }
            } catch (NumberFormatException ignored) {
                redis.delete(key(account));
            }
        }
    }

    public void recordFailure(String account) {
        Long count = redis.opsForValue().increment(key(account));
        if (count != null && count == 1) {
            redis.expire(key(account), WINDOW);
        }
        if (count != null && count >= MAX_FAILURES) {
            throw new BusinessException(ErrorCode.RATE_LIMITED, "登录失败次数过多，请 15 分钟后再试");
        }
    }

    public void clear(String account) {
        redis.delete(key(account));
    }

    private String key(String account) {
        return "login:fail:" + account.toLowerCase(Locale.ROOT);
    }
}
