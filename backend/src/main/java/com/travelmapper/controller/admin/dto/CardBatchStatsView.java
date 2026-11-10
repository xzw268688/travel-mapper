package com.travelmapper.controller.admin.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CardBatchStatsView(
        String batchId,
        String type,
        Integer value,
        BigDecimal priceCny,
        LocalDateTime createdAt,
        long total,
        long used,
        long unused,
        long voided,
        long expired,
        BigDecimal estimatedRevenueCny) {
}
