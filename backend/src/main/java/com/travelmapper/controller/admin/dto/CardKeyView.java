package com.travelmapper.controller.admin.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CardKeyView(
        Long id,
        String batchId,
        String code,
        String type,
        Integer value,
        BigDecimal priceCny,
        String status,
        LocalDateTime expireAt,
        Long usedBy,
        LocalDateTime usedAt,
        LocalDateTime createdAt) {
}