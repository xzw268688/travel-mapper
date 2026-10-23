package com.travelmapper.controller.admin.dto;

import java.time.LocalDateTime;

public record AdminQuotaResponse(
        Long userId,
        String type,
        int packBalanceAfter,
        LocalDateTime vipExpireAtAfter) {
}
