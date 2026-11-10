package com.travelmapper.service.itinerary;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ItinerarySpotFieldParsingTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void missingExtendedFieldDegradesToEmpty() throws Exception {
        JsonNode spot = objectMapper.readTree("{\"name\":\"浅草寺\"}");
        assertThat(ItineraryGenerationService.safeText(spot, "ticketPrice", 64)).isEmpty();
        assertThat(ItineraryGenerationService.safeText(spot, "warningNotes", 255)).isEmpty();
        assertThat(ItineraryGenerationService.safeBoolean(spot, "reservationRequired")).isFalse();
    }

    @Test
    void malformedExtendedFieldDegradesToEmpty() throws Exception {
        JsonNode spot = objectMapper.readTree(
                "{\"name\":\"浅草寺\",\"ticketPrice\":{\"amount\":100},\"highlights\":[1,2]}");
        assertThat(ItineraryGenerationService.safeText(spot, "ticketPrice", 64)).isEmpty();
        assertThat(ItineraryGenerationService.safeText(spot, "highlights", 255)).isEmpty();
    }

    @Test
    void overlongExtendedFieldIsTruncated() throws Exception {
        JsonNode spot = objectMapper.readTree(
                "{\"name\":\"浅草寺\",\"category\":\"" + "很".repeat(40) + "\"}");
        assertThat(ItineraryGenerationService.safeText(spot, "category", 32)).hasSize(32);
    }

    @Test
    void reservationFlagsAcceptBooleanNumberAndCommonText() throws Exception {
        assertThat(ItineraryGenerationService.safeBoolean(
                objectMapper.readTree("{\"reservationRequired\":true}"), "reservationRequired")).isTrue();
        assertThat(ItineraryGenerationService.safeBoolean(
                objectMapper.readTree("{\"reservationRequired\":1}"), "reservationRequired")).isTrue();
        assertThat(ItineraryGenerationService.safeBoolean(
                objectMapper.readTree("{\"reservationRequired\":\"需要预约\"}"), "reservationRequired")).isTrue();
        assertThat(ItineraryGenerationService.safeBoolean(
                objectMapper.readTree("{\"reservationRequired\":\"否\"}"), "reservationRequired")).isFalse();
        assertThat(ItineraryGenerationService.safeBoolean(
                objectMapper.readTree("{\"reservationRequired\":null}"), "reservationRequired")).isFalse();
    }

    @Test
    void legacyCachedGenerationWithoutExtendedFieldsStillDeserializes() throws Exception {
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        String legacy = """
                {"countryCode":"JP","startDate":"2026-01-01","endDate":"2026-01-01",
                 "days":[{"dayIndex":1,"date":"2026-01-01","cityCode":"TYO","cityName":"东京",
                          "spots":[{"name":"浅草寺","description":"d","suggestedDuration":"2h","source":"AI"}]}],
                 "guides":[],"preparations":null,"warnings":[]}
                """;
        CachedGeneration generation = mapper.readValue(legacy, CachedGeneration.class);
        var spot = generation.days().get(0).spots().get(0);
        assertThat(spot.name()).isEqualTo("浅草寺");
        assertThat(spot.category()).isNull();
        assertThat(spot.reservationRequired()).isFalse();
    }
}