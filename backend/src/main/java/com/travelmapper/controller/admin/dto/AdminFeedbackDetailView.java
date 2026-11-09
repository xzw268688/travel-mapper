package com.travelmapper.controller.admin.dto;

import com.travelmapper.repository.enums.FeedbackStatus;
import com.travelmapper.repository.enums.FeedbackType;

import java.time.LocalDateTime;

public record AdminFeedbackDetailView(
        Long id,
        Long userId,
        String submitter,
        FeedbackType type,
        String title,
        String content,
        String contact,
        String pagePath,
        String userAgent,
        String appVersion,
        FeedbackStatus status,
        String adminNote,
        Long handledBy,
        String handledByName,
        LocalDateTime handledAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}