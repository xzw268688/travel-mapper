package com.travelmapper.controller.user.dto;

import com.travelmapper.service.quota.QuotaSummary;

import java.time.LocalDateTime;

public record ProfileResponse(
        Long id,
        String username,
        String email,
        String nickname,
        String avatarPreset,
        String role,
        boolean twoFactorEnabled,
        LocalDateTime vipExpireAt,
        LocalDateTime createdAt,
        QuotaSummary quota) {
}
