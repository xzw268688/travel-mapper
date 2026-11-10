package com.travelmapper.controller.admin.dto;

import java.time.LocalDateTime;

public record AdminNoteView(
        Long id,
        String title,
        Long authorId,
        String authorName,
        String countryCode,
        String countryName,
        String cityCode,
        String cityName,
        String visibility,
        boolean takenDown,
        String takedownReason,
        boolean hasMedia,
        LocalDateTime mediaReviewedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
