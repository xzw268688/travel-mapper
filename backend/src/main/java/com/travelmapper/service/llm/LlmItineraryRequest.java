package com.travelmapper.service.llm;

import java.time.LocalDate;
import java.util.List;

public record LlmItineraryRequest(
        String countryCode,
        String countryName,
        List<String> cityCodes,
        List<String> cityNames,
        int days,
        LocalDate startDate,
        String preference) {
}
