package com.travelmapper.controller.note.dto;

import com.travelmapper.repository.enums.ReportReason;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

public final class ReportDtos {

    private ReportDtos() {
    }

    @Data
    public static class CreateRequest {
        @NotNull
        private ReportReason reason;
        @Size(max = 200)
        private String description;
    }

    public record Receipt(Long reportId, boolean duplicate, LocalDateTime createdAt) {
    }
}