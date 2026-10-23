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
        LocalDateTime vipExpireAt,
        LocalDateTime createdAt,
        QuotaSummary quota) {
}
