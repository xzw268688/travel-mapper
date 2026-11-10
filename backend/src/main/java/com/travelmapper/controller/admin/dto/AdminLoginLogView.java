package com.travelmapper.controller.admin.dto;

import java.time.LocalDateTime;

public record AdminLoginLogView(
        Long id,
        Long userId,
        String username,
        String role,
        boolean success,
        String reason,
        String ip,
        String userAgent,
        LocalDateTime createdAt) {
}