package com.travelmapper.service.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.travelmapper.common.log.SensitiveDataMasker;
import com.travelmapper.repository.entity.AuditLog;
import com.travelmapper.repository.mapper.AuditLogMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditLogService {

    private final AuditLogMapper mapper;
    private final ObjectMapper objectMapper;

    public AuditLogService(AuditLogMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void record(Long userId, String action, String targetType, Long targetId, Object detail, String ip) {
        AuditLog log = new AuditLog();
        log.setUserId(userId);
        log.setAction(action);
        log.setTargetType(targetType);
        log.setTargetId(targetId);
        log.setIp(ip);
        if (detail != null) {
            try {
                log.setDetail(SensitiveDataMasker.mask(objectMapper.writeValueAsString(detail)));
            } catch (JsonProcessingException ex) {
                log.setDetail("{\"error\":\"detail-serialization-failed\"}");
            }
        }
        mapper.insert(log);
    }
}
