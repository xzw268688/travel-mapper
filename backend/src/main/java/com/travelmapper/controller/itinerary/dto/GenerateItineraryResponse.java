package com.travelmapper.controller.itinerary.dto;

import com.travelmapper.service.search.GuideLink;

import java.time.LocalDate;
import java.util.List;

public record GenerateItineraryResponse(
        String requestId,
        String countryCode,
        LocalDate startDate,
        LocalDate endDate,
        List<DayPlan> days,
        List<GuideLink> guides,
        Preparations preparations,
        List<String> warnings,
        boolean deduplicated,
        QuotaInfo quota) {

    public record DayPlan(
            int dayIndex,
            LocalDate date,
            String cityCode,
            String cityName,
            List<SpotPlan> spots) {
    }

    public record SpotPlan(
            String name,
            String description,
            String suggestedDuration,
            String source) {
    }

    public record Preparations(
            String visa,
            String currency,
            String powerPlug,
            String bestSeason,
            List<String> notes) {
    }

    public record QuotaInfo(String source, int freeUsedToday, int freeLimit, int packBalance) {
    }
}
