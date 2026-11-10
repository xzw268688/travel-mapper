package com.travelmapper.controller.admin.dto;

import com.travelmapper.repository.enums.ReportReason;
import com.travelmapper.repository.enums.ReportStatus;

import java.time.LocalDateTime;

public record AdminReportDetailView(
        Long id,
        Long noteId,
        Long reporterUserId,
        String reporterName,
        ReportReason reason,
        String description,
        String noteTitle,
        String noteContentText,
        LocalDateTime noteUpdatedAt,
        ReportStatus status,
        Long handledBy,
        String handledByName,
        LocalDateTime handledAt,
        LocalDateTime createdAt,
        Long authorId,
        String authorName,
        String currentNoteTitle,
        String currentNoteVisibility,
        LocalDateTime currentNoteUpdatedAt,
        boolean currentNoteTakedown,
        boolean currentNoteDeleted,
        long authorAcceptedReportCount) {
}