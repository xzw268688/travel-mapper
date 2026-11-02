package com.travelmapper.controller.trip.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 旅行详情聚合：概览 + 汇总 + 行程/游记全部 + 最近 10 条消费。 */
public record TripDetailView(
        Long id,
        String name,
        String countryCode,
        String countryName,
        LocalDate startDate,
        LocalDate endDate,
        String status,
        Summary summary,
        List<ItineraryItem> itineraries,
        List<NoteItem> notes,
        List<ExpenseItem> recentExpenses) {

    public record Summary(
            int itineraryCount,
            int noteCount,
            int expenseCount,
            String expenseCny,
            boolean hasUnconfiguredCurrency,
            List<String> unconfiguredCurrencies) {
    }

    public record ItineraryItem(
            Long id,
            String title,
            LocalDate startDate,
            LocalDate endDate,
            String status,
            boolean expired) {
    }

    public record NoteItem(
            Long id,
            String title,
            String visibility,
            String cityCode,
            String cityName,
            boolean takedown,
            LocalDateTime createdAt) {
    }

    public record ExpenseItem(
            Long id,
            LocalDate expenseDate,
            String category,
            String amount,
            String currency,
            String amountCny,
            boolean rateMissing,
            String remark) {
    }
}