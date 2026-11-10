package com.travelmapper.controller.trip.dto;

import java.time.LocalDate;

public record TripStatusResponse(
        Long id,
        String name,
        String status,
        String effectiveStatus,
        boolean statusManual,
        boolean expired,
        LocalDate effectiveStartDate,
        LocalDate effectiveEndDate) {
}