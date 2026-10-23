package com.travelmapper.controller.prep.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

public final class ChecklistDtos {

    private ChecklistDtos() {
    }

    public record ChecklistResponse(
            Long itineraryId,
            String countryCode,
            List<Item> items) {
    }

    public record Item(String itemKey, String label, boolean checked) {
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
}
