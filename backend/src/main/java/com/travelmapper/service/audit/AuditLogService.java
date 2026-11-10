package com.travelmapper.service.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.travelmapper.common.log.SensitiveDataMasker;
import com.travelmapper.common.security.RequestContext;
import com.travelmapper.common.security.RequestIdentity;
import com.travelmapper.repository.entity.AuditLog;
import com.travelmapper.repository.entity.User;
import com.travelmapper.repository.mapper.AuditLogMapper;
import com.travelmapper.repository.mapper.UserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Slf4j
@Service
public class AuditLogService {

    public static final String ROLE_ADMIN = "ADMIN";
    public static final String ROLE_USER = "USER";
    public static final String ROLE_SYSTEM = "SYSTEM";
    public static final String ROLE_ANONYMOUS = "ANONYMOUS";

    private static final int IP_MAX_LENGTH = 45;

    private final AuditLogMapper mapper;
    private final UserMapper userMapper;
    private final ObjectMapper objectMapper;

    public AuditLogService(AuditLogMapper mapper, UserMapper userMapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.userMapper = userMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 审计写入尽力而为：任何异常只告警，不得影响业务操作。
     */
    @Transactional
    public void record(Long userId, String action, String targetType, Long targetId, Object detail, String ip) {
        try {
            AuditLog entry = new AuditLog();
            entry.setUserId(userId);
            entry.setActorRole(resolveActorRole(userId));
            entry.setAction(action);
            entry.setTargetType(targetType);
            entry.setTargetId(targetId);
            entry.setIp(resolveIp(ip));
            if (detail != null) {
                try {
                    entry.setDetail(SensitiveDataMasker.mask(objectMapper.writeValueAsString(detail)));
                } catch (JsonProcessingException ex) {
                    entry.setDetail("{\"error\":\"detail-serialization-failed\"}");
                }
            }
            mapper.insert(entry);
        } catch (Exception ex) {
            log.warn("audit write failed: action={}, userId={}", action, userId, ex);
        }
    }

    private String resolveActorRole(Long userId) {
        if (userId == null) {
            return hasHttpRequest() ? ROLE_ANONYMOUS : ROLE_SYSTEM;
        }
        RequestIdentity identity = RequestContext.get();
        if (userId.equals(identity.userId())
                && identity.role() != null
                && !ROLE_ANONYMOUS.equals(identity.role())) {
            return identity.role();
        }
        User user = userMapper.selectById(userId);
        if (user == null || user.getRole() == null) {
            return ROLE_SYSTEM;
        }
        return user.getRole().name();
    }

    private String resolveIp(String ip) {
        if (ip != null && !ip.isBlank()) {
            return truncateIp(ip);
        }
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            String forwarded = attributes.getRequest().getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                String first = forwarded.split(",")[0].trim();
                if (!first.isEmpty()) {
                    return truncateIp(first);
                }
            }
            return truncateIp(attributes.getRequest().getRemoteAddr());
        }
        return null;
    }

    private String truncateIp(String value) {
        String trimmed = value.trim();
        return trimmed.length() <= IP_MAX_LENGTH ? trimmed : trimmed.substring(0, IP_MAX_LENGTH);
    }

    private boolean hasHttpRequest() {
        return RequestContextHolder.getRequestAttributes() != null;
    }
}