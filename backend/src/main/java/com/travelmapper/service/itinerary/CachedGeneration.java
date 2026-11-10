package com.travelmapper.service.itinerary;

import com.travelmapper.controller.itinerary.dto.GenerateItineraryResponse;
import com.travelmapper.service.search.GuideLink;

import java.time.LocalDate;
import java.util.List;

public record CachedGeneration(
        String countryCode,
        LocalDate startDate,
        LocalDate endDate,
        List<GenerateItineraryResponse.DayPlan> days,
        List<GuideLink> guides,
        GenerateItineraryResponse.Preparations preparations,
        List<String> warnings) {
}
