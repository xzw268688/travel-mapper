package com.travelmapper.controller.admin.dto;

import com.travelmapper.repository.enums.ReportReason;
import com.travelmapper.repository.enums.ReportStatus;

import java.time.LocalDateTime;

public record AdminReportView(
        Long id,
        Long noteId,
        String reporterName,
        ReportReason reason,
        ReportStatus status,
        String description,
        String noteTitle,
        String currentNoteTitle,
        String authorName,
        LocalDateTime createdAt,
        LocalDateTime handledAt) {
}