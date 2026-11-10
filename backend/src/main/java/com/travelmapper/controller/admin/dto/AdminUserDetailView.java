package com.travelmapper.controller.admin.dto;

import java.time.LocalDateTime;
import java.util.List;

public record AdminUserDetailView(
        Long id,
        String username,
        String nickname,
        String email,
        String role,
        String status,
        boolean twoFactorEnabled,
        LocalDateTime vipExpireAt,
        int freeUsedToday,
        int packBalance,
        long noteCount,
        long itineraryCount,
        long tripCount,
        LocalDateTime createdAt,
        List<CardRedemptionView> recentRedemptions) {
}
