package com.travelmapper.controller.admin.dto;

import java.time.LocalDateTime;

public record AdminCityView(
        Long id,
        String countryCode,
        String cityCode,
        String name,
        String level,
        String status,
        boolean referenced,
        LocalDateTime createdAt) {
}
