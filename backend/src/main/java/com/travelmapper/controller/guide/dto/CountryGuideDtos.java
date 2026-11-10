package com.travelmapper.controller.guide.dto;

import java.util.List;

public final class CountryGuideDtos {

    private CountryGuideDtos() {
    }

    public record View(
            String countryCode,
            String countryName,
            String intro,
            List<Attraction> attractions,
            Notice notice,
            boolean cached) {

        public View withCached(boolean value) {
            return new View(countryCode, countryName, intro, attractions, notice, value);
        }
    }

    public record CacheView(boolean available, View guide) {
    }

    public record Attraction(String name, String description) {
    }

    public record Notice(String visa, String currency, String powerPlug,
                         String bestSeason, List<String> notes) {
    }
}