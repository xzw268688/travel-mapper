package com.travelmapper.controller.trip.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record TripView(
        Long id,
        String countryCode,
        String name,
        LocalDate startDate,
        LocalDate endDate,
        LocalDate effectiveStartDate,
        LocalDate effectiveEndDate,
        int itineraryCount,
        int noteCount,
        int expenseCount,
        String expenseCny,
        String status,
        String effectiveStatus,
        boolean statusManual,
        boolean expired,
        LocalDateTime createdAt) {
}
