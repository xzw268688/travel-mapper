package com.travelmapper.controller.trip.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record TripView(
        Long id,
        String countryCode,
        String name,
        LocalDate startDate,
        LocalDate endDate,
        int itineraryCount,
        int noteCount,
        int expenseCount,
        String expenseCny,
        LocalDateTime createdAt) {
}
