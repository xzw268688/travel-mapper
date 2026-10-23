package com.travelmapper.controller.admin.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDateTime;

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
}
