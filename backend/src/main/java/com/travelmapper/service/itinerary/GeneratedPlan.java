package com.travelmapper.service.itinerary;

import com.travelmapper.controller.itinerary.dto.GenerateItineraryResponse;

import java.util.List;

public record GeneratedPlan(
        List<GenerateItineraryResponse.DayPlan> days,
        GenerateItineraryResponse.Preparations preparations,
        List<String> warnings) {
}
