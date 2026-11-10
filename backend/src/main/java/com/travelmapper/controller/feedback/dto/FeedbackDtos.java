package com.travelmapper.controller.feedback.dto;

import com.travelmapper.repository.enums.FeedbackStatus;
import com.travelmapper.repository.enums.FeedbackType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

public final class FeedbackDtos {

    private FeedbackDtos() {
    }

    @Data
    public static class CreateRequest {
        @NotNull
        private FeedbackType type;
        @NotBlank
        @Size(max = 50)
        private String title;
        @NotBlank
        @Size(min = 10, max = 1000)
        private String content;
        @Size(max = 100)
        private String contact;
        @Size(max = 255)
        private String pagePath;
        @Size(max = 32)
        private String appVersion;
    }

    public record CreateResponse(
            Long id,
            FeedbackType type,
            String title,
            String content,
            String contact,
            FeedbackStatus status,
            LocalDateTime createdAt) {
    }

    public record HistoryView(
            Long id,
            FeedbackType type,
            String title,
            String content,
            String contact,
            FeedbackStatus status,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
    }
}