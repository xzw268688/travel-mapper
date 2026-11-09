package com.travelmapper.service.audit;

import com.travelmapper.repository.entity.LoginLog;
import com.travelmapper.repository.mapper.LoginLogMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 登录日志写入：尽力而为，任何异常都不得影响登录本身。
 */
@Slf4j
@Service
public class LoginLogService {

    public static final String SUCCESS = "SUCCESS";
    public static final String USER_NOT_FOUND = "USER_NOT_FOUND";
    public static final String BAD_CREDENTIALS = "BAD_CREDENTIALS";
    public static final String ACCOUNT_BANNED = "ACCOUNT_BANNED";
    public static final String ACCOUNT_DELETED = "ACCOUNT_DELETED";
    public static final String EMAIL_NOT_VERIFIED = "EMAIL_NOT_VERIFIED";
    public static final String RATE_LIMITED = "RATE_LIMITED";

    private static final int USERNAME_MAX_LENGTH = 64;
    private static final int ROLE_MAX_LENGTH = 16;
    private static final int REASON_MAX_LENGTH = 32;
    private static final int IP_MAX_LENGTH = 45;
    private static final int USER_AGENT_MAX_LENGTH = 255;

    private final LoginLogMapper mapper;

    public LoginLogService(LoginLogMapper mapper) {
        this.mapper = mapper;
    }

    public void record(Long userId, String username, String role, boolean success,
                       String reason, String ip, String userAgent) {
        try {
            LoginLog entry = new LoginLog();
            entry.setUserId(userId);
            entry.setUsername(truncate(username, USERNAME_MAX_LENGTH));
            entry.setRole(truncate(role, ROLE_MAX_LENGTH));
            entry.setSuccess(success);
            entry.setReason(success ? SUCCESS : truncate(reason, REASON_MAX_LENGTH));
            entry.setIp(truncate(ip, IP_MAX_LENGTH));
            entry.setUserAgent(truncate(userAgent, USER_AGENT_MAX_LENGTH));
            mapper.insert(entry);
        } catch (Exception ex) {
            log.warn("login log write failed", ex);
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }
}