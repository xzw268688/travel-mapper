package com.travelmapper.controller.admin.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CardRedemptionView(
        Long cardId,
        String code,
        String batchId,
        String type,
        Integer value,
        BigDecimal priceCny,
        Long userId,
        String username,
        LocalDateTime usedAt) {
}
