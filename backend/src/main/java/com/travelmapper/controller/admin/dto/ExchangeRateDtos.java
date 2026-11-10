package com.travelmapper.controller.admin.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

public final class ExchangeRateDtos {

    private ExchangeRateDtos() {
    }

    public record RateView(Long id, String currency, String rateToCny, LocalDateTime updatedAt) {
    }

    @Data
    public static class UpdateRequest {
        @NotBlank
        private String currency;
        @NotBlank
        private String rateToCny;
    }

    public record UpdateResponse(
            String currency,
            String rateToCny,
            String previousRate,
            boolean created,
            int affectedUnconfiguredRecords) {
    }

    public record RefreshItem(
            String currency,
            String oldRate,
            String newRate,
            String status) {
    }

    public record RefreshResponse(
            String source,
            LocalDateTime fetchedAt,
            boolean applied,
            int updatedCount,
            int unchangedCount,
            int unsupportedCount,
            List<RefreshItem> items) {
    }
}
