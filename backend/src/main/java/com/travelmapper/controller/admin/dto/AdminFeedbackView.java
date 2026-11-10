package com.travelmapper.controller.admin.dto;

import com.travelmapper.repository.enums.FeedbackStatus;
import com.travelmapper.repository.enums.FeedbackType;

import java.time.LocalDateTime;

public record AdminFeedbackView(
        Long id,
        Long userId,
        String submitter,
        FeedbackType type,
        String title,
        FeedbackStatus status,
        String contact,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime handledAt) {
}