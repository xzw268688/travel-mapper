package com.travelmapper.controller.prep.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

public final class ChecklistDtos {

    private ChecklistDtos() {
    }

    public record Item(
            Long id,
            String itemKey,
            String label,
            String title,
            String description,
            Integer daysBefore,
            boolean checked,
            boolean custom,
            int sortOrder) {
    }

    public record ChecklistResponse(
            Long itineraryId,
            String countryCode,
            List<Item> items) {
    }

    @Data
    public static class UpdateRequest {
        @NotNull
        private Long itineraryId;
        @NotBlank
        private String itemKey;
        @NotNull
        private Boolean checked;
    }

    public record UpdateResponse(String itemKey, boolean checked, LocalDateTime updatedAt) {
    }

    @Data
    public static class AddItemRequest {
        @NotNull
        private Long itineraryId;
        @NotBlank
        @Size(max = 64)
        private String title;
        @Size(max = 255)
        private String description;
        @NotNull
        @Min(0)
        @Max(365)
        private Integer daysBefore;
    }

    public record DeleteResponse(Long id) {
    }

    @Data
    public static class BatchRequest {
        @NotNull
        private Long itineraryId;
        @NotNull
        private List<BatchItem> items;
    }

    @Data
    public static class BatchItem {
        private String itemKey;
        @Size(max = 64)
        private String title;
        @Size(max = 255)
        private String description;
        @Min(0)
        @Max(365)
        private Integer daysBefore;
        private Boolean checked;
        private Boolean custom;
    }
}