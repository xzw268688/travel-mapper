package com.travelmapper.controller.admin.dto;

import com.travelmapper.controller.note.dto.NoteDtos;

import java.time.LocalDateTime;
import java.util.List;

public record AdminNoteDetailView(
        Long id,
        String title,
        String content,
        String contentText,
        List<NoteDtos.ImageView> images,
        List<NoteDtos.VideoView> videos,
        String coverImageUrl,
        Long authorId,
        String authorName,
        String countryCode,
        String countryName,
        String cityCode,
        String cityName,
        String visibility,
        boolean hasActiveShare,
        LocalDateTime shareExpireAt,
        boolean takedown,
        String takedownReason,
        LocalDateTime takedownAt,
        LocalDateTime mediaReviewedAt,
        Long mediaReviewedBy,
        boolean deleted,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}