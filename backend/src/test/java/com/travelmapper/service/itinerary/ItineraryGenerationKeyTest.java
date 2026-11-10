package com.travelmapper.service.itinerary;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ItineraryGenerationKeyTest {

    @Test
    void preferenceNormalizationIsStable() {
        assertThat(ItineraryGenerationService.normalizePreference(" 美食，美食！ Hello "))
                .isEqualTo("hello 美食");
        assertThat(ItineraryGenerationService.normalizePreference("美食 美食"))
                .isEqualTo("美食");
    }

    @Test
    void fingerprintIgnoresCityOrderButNotInputs() {
        String first = ItineraryGenerationService.fingerprint("JP",
                List.of("OSA", "TYO"), 3, "美食");
        String second = ItineraryGenerationService.fingerprint("JP",
                List.of("TYO", "OSA"), 3, "美食");
        assertThat(first).isEqualTo(second);
        assertThat(ItineraryGenerationService.fingerprint("JP",
                List.of("TYO", "OSA"), 4, "美食")).isNotEqualTo(first);
    }
}
