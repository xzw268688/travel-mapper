package com.travelmapper.controller.map.dto;

import java.util.List;

public record MapStatsResponse(List<CountryStat> countries) {

    public record CountryStat(
            String countryCode,
            String countryName,
            String status,
            int itineraryCount,
            int noteCount,
            int totalCount,
            boolean expired) {
    }
}
