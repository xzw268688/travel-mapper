package com.travelmapper.service.search;

import java.util.List;

public record GuideSearchQuery(
        String countryCode,
        String countryName,
        List<String> cityCodes,
        List<String> cityNames,
        int days,
        String normalizedPreference) {
}
