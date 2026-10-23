package com.travelmapper.controller.admin.dto;

import java.time.LocalDateTime;

public record CardKeyView(
        Long id,
        String batchId,
        String code,
        String type,
        Integer value,
        String status,
        LocalDateTime expireAt,
        Long usedBy,
        LocalDateTime usedAt,
        LocalDateTime createdAt) {
}
