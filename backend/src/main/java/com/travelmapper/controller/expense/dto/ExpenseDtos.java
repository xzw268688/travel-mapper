package com.travelmapper.controller.expense.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class ExpenseDtos {

    private ExpenseDtos() {
    }

    @Data
    public static class CreateRequest {
        @NotBlank
        private String countryCode;
        private String cityCode;
        private Long itineraryId;
        private Long tripId;
        @NotBlank
        private String currency;
        @NotBlank
        private String amount;
        @NotBlank
        private String category;
        @NotNull
        private LocalDate expenseDate;
        @Size(max = 100)
        private String remark;
    }

    @Data
    public static class UpdateRequest {
        private String countryCode;
        private String cityCode;
        private Long itineraryId;
        private Long tripId;
        private String currency;
        private String amount;
        private String category;
        private LocalDate expenseDate;
        @Size(max = 100)
        private String remark;
    }

    public record Response(
            Long id,
            String countryCode,
            String countryName,
            String cityCode,
            String cityName,
            Long tripId,
            String currency,
            String amount,
            String exchangeRateSnapshot,
            String amountCny,
            boolean rateMissing,
            String category,
            LocalDate expenseDate,
            String remark,
            LocalDateTime createdAt) {
    }

    public record CreateResponse(
            Long id,
            String currency,
            String amount,
            String exchangeRateSnapshot,
            String amountCny,
            boolean rateMissing,
            Long tripId,
            boolean tripInherited) {
    }

    public record DeleteResponse(Long id) {
    }

    public record SummaryResponse(
            List<String> groupBy,
            String currency,
            String totalCny,
            boolean hasUnconfiguredCurrency,
            List<String> unconfiguredCurrencies,
            List<Group> groups) {
    }

    public record Group(
            String key,
            String label,
            String totalCny,
            List<CategoryTotal> categories,
            List<CurrencyTotal> originalCurrencies) {
    }

    public record CategoryTotal(String category, String totalCny) {
    }

    public record CurrencyTotal(String currency, String total) {
    }
}
