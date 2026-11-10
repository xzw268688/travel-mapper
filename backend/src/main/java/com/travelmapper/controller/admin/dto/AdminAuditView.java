package com.travelmapper.controller.admin.dto;

import java.time.LocalDateTime;

public record AdminAuditView(
        Long id,
        Long userId,
        String actorRole,
        String username,
        String action,
        String targetType,
        Long targetId,
        String detail,
        String ip,
        LocalDateTime createdAt) {
}
