package com.travelmapper.controller.admin.dto;

import java.time.LocalDateTime;

public record AdminUserView(
        Long id,
        String username,
        String nickname,
        String email,
        String role,
        String status,
        LocalDateTime vipExpireAt,
        int freeUsedToday,
        int packBalance,
        LocalDateTime createdAt) {
}
