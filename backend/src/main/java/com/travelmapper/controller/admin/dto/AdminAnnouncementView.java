package com.travelmapper.controller.admin.dto;

import com.travelmapper.repository.enums.AnnouncementStatus;
import com.travelmapper.repository.enums.AnnouncementType;

import java.time.LocalDateTime;

public record AdminAnnouncementView(
        Long id,
        String title,
        AnnouncementType type,
        boolean pinned,
        AnnouncementStatus status,
        LocalDateTime publishAt,
        LocalDateTime expireAt,
        long readCount,
        long totalUsers,
        double readRate,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}