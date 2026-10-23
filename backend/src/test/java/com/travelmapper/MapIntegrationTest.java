package com.travelmapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.travelmapper.repository.entity.User;
import com.travelmapper.service.map.MapService;
import com.travelmapper.service.trip.TripService;
import com.travelmapper.support.AuthTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MapIntegrationTest extends AuthTestSupport {

    @Autowired
    private MapService mapService;

    @Autowired
    private TripService tripService;

    @Test
    void statsCalculateGreenYellowGrayAndExpired() throws Exception {
        User user = createVerifiedUser("mapstats");
        insertItinerary(user, null, "JP", "过期行程",
                LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 5));

        JsonNode jp = findCountry(stats(user), "JP");
        assertThat(jp.path("status").asText()).isEqualTo("YELLOW");
        assertThat(jp.path("expired").asBoolean()).isTrue();
        assertThat(jp.path("itineraryCount").asInt()).isEqualTo(1);

        long unexpiredId = insertItinerary(user, null, "JP", "进行中行程",
                LocalDate.of(2026, 10, 20), LocalDate.of(2026, 10, 30));
        jp = findCountry(stats(user), "JP");
        assertThat(jp.path("status").asText()).isEqualTo("YELLOW");
        assertThat(jp.path("expired").asBoolean()).isFalse();

        long noteId = insertNote(user, null, "JP", "TYO", "东京游记", "PRIVATE");
        jp = findCountry(stats(user), "JP");
        assertThat(jp.path("status").asText()).isEqualTo("GREEN");
        assertThat(jp.path("itineraryCount").asInt()).isEqualTo(2);
        assertThat(jp.path("noteCount").asInt()).isEqualTo(1);
        assertThat(jp.path("totalCount").asInt()).isEqualTo(3);
        assertThat(jp.path("expired").asBoolean()).isFalse();

        jdbcTemplate.update("UPDATE travel_note SET deleted = 1 WHERE id = ?", noteId);
        jp = findCountry(stats(user), "JP");
        assertThat(jp.path("status").asText()).isEqualTo("YELLOW");
        assertThat(jp.path("expired").asBoolean()).isFalse();

        jdbcTemplate.update("UPDATE itinerary SET deleted = 1 WHERE id = ?", unexpiredId);
        jp = findCountry(stats(user), "JP");
        assertThat(jp.path("status").asText()).isEqualTo("YELLOW");
        assertThat(jp.path("expired").asBoolean()).isTrue();

        jdbcTemplate.update("UPDATE itinerary SET deleted = 1 WHERE user_id = ? AND country_code = 'JP'", user.getId());
        assertThat(findCountry(stats(user), "JP")).isNull();

        insertNote(user, null, "TH", "BKK", "曼谷游记", "PRIVATE");
        long deletedNote = insertNote(user, null, "TH", "BKK", "已删游记", "PRIVATE");
        jdbcTemplate.update("UPDATE travel_note SET deleted = 1 WHERE id = ?", deletedNote);
        JsonNode th = findCountry(stats(user), "TH");
        assertThat(th.path("status").asText()).isEqualTo("GREEN");
        assertThat(th.path("noteCount").asInt()).isEqualTo(1);
    }

    @Test
    void drawerGroupsTripsCitiesAndUngroupedData() throws Exception {
        User user = createVerifiedUser("mapdrawer");
        long trip1 = insertTrip(user, "JP", "日本 · 2025-06",
                LocalDate.of(2025, 6, 1), LocalDate.of(2025, 6, 7));
        long trip2 = insertTrip(user, "JP", "日本 · 2025-08",
                LocalDate.of(2025, 8, 1), LocalDate.of(2025, 8, 5));

        long multiCity = insertItinerary(user, trip1, "JP", "多城市",
                LocalDate.of(2025, 6, 1), LocalDate.of(2025, 6, 2));
        insertDay(multiCity, 1, "TYO");
        insertDay(multiCity, 2, "OSA");
        long tokyoOnly = insertItinerary(user, trip1, "JP", "东京行程",
                LocalDate.of(2025, 6, 3), LocalDate.of(2025, 6, 4));
        insertDay(tokyoOnly, 1, "TYO");
        long ungroupedItinerary = insertItinerary(user, null, "JP", "未归组行程",
                LocalDate.of(2025, 5, 1), LocalDate.of(2025, 5, 2));
        insertDay(ungroupedItinerary, 1, "TYO");

        insertNote(user, trip1, "JP", "TYO", "东京散步", "PRIVATE");
        insertNote(user, trip2, "JP", "OSA", "大阪随笔", "PRIVATE");
        insertNote(user, null, "JP", "TYO", "未归组随笔", "PRIVATE");

        insertExpense(user, trip1, "JP", "TYO", "JPY", new BigDecimal("100.00"), new BigDecimal("0.050000"));
        insertExpense(user, trip2, "JP", "OSA", "USD", new BigDecimal("10.00"), new BigDecimal("7.000000"));
        insertExpense(user, null, "JP", "TYO", "KRW", new BigDecimal("200.00"), null);

        MvcResult result = mockMvc.perform(withToken(get("/api/map/country/JP"), user))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");

        assertThat(data.path("status").asText()).isEqualTo("GREEN");
        assertThat(data.path("summary").path("itineraryCount").asInt()).isEqualTo(3);
        assertThat(data.path("summary").path("noteCount").asInt()).isEqualTo(3);
        assertThat(data.path("summary").path("totalExpenseCny").asText()).isEqualTo("75.00");
        assertThat(data.path("summary").path("hasUnconfiguredCurrency").asBoolean()).isTrue();
        assertThat(data.path("summary").path("unconfiguredCurrencies").get(0).asText()).isEqualTo("KRW");

        JsonNode statsJp = findCountry(stats(user), "JP");
        assertThat(statsJp.path("itineraryCount").asInt()).isEqualTo(3);
        assertThat(statsJp.path("noteCount").asInt()).isEqualTo(3);
        assertThat(statsJp.path("totalCount").asInt()).isEqualTo(6);

        JsonNode trips = data.path("trips");
        assertThat(trips.size()).isEqualTo(2);
        assertThat(trips.get(0).path("tripId").asLong()).isEqualTo(trip2);
        assertThat(trips.get(0).path("subtotalCny").asText()).isEqualTo("70.00");
        assertThat(trips.get(0).path("cities").get(0).path("cityCode").asText()).isEqualTo("OSA");
        assertThat(trips.get(0).path("cities").get(0).path("cityLabel").asText()).isEqualTo("COMPLETED");

        JsonNode firstTrip = trips.get(1);
        assertThat(firstTrip.path("tripId").asLong()).isEqualTo(trip1);
        assertThat(firstTrip.path("subtotalCny").asText()).isEqualTo("5.00");
        JsonNode osa = findCity(firstTrip, "OSA");
        JsonNode tyo = findCity(firstTrip, "TYO");
        assertThat(osa.path("cityLabel").asText()).isEqualTo("PLANNED");
        assertThat(osa.path("itineraries").size()).isEqualTo(1);
        assertThat(tyo.path("cityLabel").asText()).isEqualTo("COMPLETED");
        assertThat(tyo.path("itineraries").size()).isEqualTo(2);
        assertThat(tyo.path("notes").size()).isEqualTo(1);
        assertThat(tyo.path("expenseCny").asText()).isEqualTo("5.00");

        JsonNode ungrouped = data.path("ungrouped");
        assertThat(ungrouped.path("itineraries").size()).isEqualTo(1);
        assertThat(ungrouped.path("notes").size()).isEqualTo(1);
        assertThat(ungrouped.path("expenses").size()).isEqualTo(1);
        assertThat(ungrouped.path("expenseCny").asText()).isEqualTo("0.00");
        assertThat(ungrouped.path("itineraries").get(0).path("cities").get(0).path("cityCode").asText())
                .isEqualTo("TYO");

        mockMvc.perform(withToken(get("/api/map/country/US"), user))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.data.status").value("GRAY"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.data.trips").isEmpty())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.data.ungrouped.itineraries").isEmpty())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.data.summary.totalExpenseCny").value("0.00"));

        User other = createVerifiedUser("mapother");
        insertNote(other, null, "JP", "TYO", "别人的游记", "PRIVATE");
        MvcResult isolated = mockMvc.perform(withToken(get("/api/map/country/JP"), user))
                .andExpect(status().isOk()).andReturn();
        assertThat(objectMapper.readTree(isolated.getResponse().getContentAsString())
                .path("data").path("summary").path("noteCount").asInt()).isEqualTo(3);
    }

    @Test
    void deletingTripKeepsMapStateAndMovesDataToUngrouped() throws Exception {
        User user = createVerifiedUser("mapdeltrip");
        long trip = insertTrip(user, "JP", "待删旅行",
                LocalDate.of(2025, 6, 1), LocalDate.of(2025, 6, 7));
        long itinerary = insertItinerary(user, trip, "JP", "旅行内行程",
                LocalDate.of(2025, 6, 1), LocalDate.of(2025, 6, 2));
        insertDay(itinerary, 1, "TYO");

        JsonNode before = findCountry(stats(user), "JP");
        assertThat(before.path("status").asText()).isEqualTo("YELLOW");
        assertThat(before.path("itineraryCount").asInt()).isEqualTo(1);

        tripService.delete(user.getId(), trip, true);

        JsonNode after = findCountry(stats(user), "JP");
        assertThat(after.path("status").asText()).isEqualTo("YELLOW");
        assertThat(after.path("itineraryCount").asInt()).isEqualTo(1);

        MvcResult result = mockMvc.perform(withToken(get("/api/map/country/JP"), user))
                .andExpect(status().isOk()).andReturn();
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        assertThat(data.path("trips").size()).isZero();
        assertThat(data.path("ungrouped").path("itineraries").size()).isEqualTo(1);
    }

    @Test
    void statsAndDrawerMeetLatencyBudget() {
        User user = createVerifiedUser("maplatency");
        long trip = insertTrip(user, "JP", "性能旅行", LocalDate.of(2025, 6, 1), LocalDate.of(2025, 6, 7));
        long itinerary = insertItinerary(user, trip, "JP", "性能行程",
                LocalDate.of(2025, 6, 1), LocalDate.of(2025, 6, 2));
        insertDay(itinerary, 1, "TYO");
        insertNote(user, trip, "JP", "TYO", "性能游记", "PRIVATE");
        insertExpense(user, trip, "JP", "TYO", "JPY", new BigDecimal("10.00"), new BigDecimal("0.050000"));

        mapService.stats(user.getId());
        mapService.drawer(user.getId(), "JP");

        List<Long> timings = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            long start = System.nanoTime();
            mapService.stats(user.getId());
            mapService.drawer(user.getId(), "JP");
            timings.add((System.nanoTime() - start) / 1_000_000);
        }
        timings.sort(Comparator.naturalOrder());
        long p95 = timings.get((int) Math.ceil(timings.size() * 0.95) - 1);
        assertThat(p95).isLessThan(500);
    }

    private JsonNode stats(User user) throws Exception {
        MvcResult result = mockMvc.perform(withToken(get("/api/map/stats"), user))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("countries");
    }

    private JsonNode findCountry(JsonNode countries, String code) {
        for (JsonNode node : countries) {
            if (node.path("countryCode").asText().equals(code)) {
                return node;
            }
        }
        return null;
    }

    private JsonNode findCity(JsonNode trip, String code) {
        for (JsonNode city : trip.path("cities")) {
            if (city.path("cityCode").asText().equals(code)) {
                return city;
            }
        }
        throw new AssertionError("city not found: " + code);
    }

    private long insertTrip(User user, String country, String name, LocalDate start, LocalDate end) {
        jdbcTemplate.update("INSERT INTO trip (user_id, country_code, name, start_date, end_date, deleted) "
                + "VALUES (?, ?, ?, ?, ?, 0)", user.getId(), country, name, start, end);
        return lastId("SELECT MAX(id) FROM trip WHERE user_id = ?", user.getId());
    }

    private long insertItinerary(User user, Long tripId, String country, String title,
                                 LocalDate start, LocalDate end) {
        jdbcTemplate.update("INSERT INTO itinerary (user_id, trip_id, country_code, title, start_date, end_date, "
                        + "status, deleted) VALUES (?, ?, ?, ?, ?, ?, 'SAVED', 0)",
                user.getId(), tripId, country, title + "-" + UUID.randomUUID(), start, end);
        return lastId("SELECT MAX(id) FROM itinerary WHERE user_id = ?", user.getId());
    }

    private void insertDay(long itineraryId, int dayIndex, String cityCode) {
        jdbcTemplate.update("INSERT INTO itinerary_day (itinerary_id, day_index, visit_date, city_code) "
                + "VALUES (?, ?, ?, ?)", itineraryId, dayIndex, LocalDate.now(), cityCode);
    }

    private long insertNote(User user, Long tripId, String country, String city, String title,
                            String visibility) {
        jdbcTemplate.update("INSERT INTO travel_note (user_id, trip_id, country_code, city_code, title, content, "
                        + "content_text, visibility, takedown, deleted) VALUES (?, ?, ?, ?, ?, '<p>x</p>', 'x', ?, 0, 0)",
                user.getId(), tripId, country, city, title, visibility);
        return lastId("SELECT MAX(id) FROM travel_note WHERE user_id = ?", user.getId());
    }

    private void insertExpense(User user, Long tripId, String country, String city, String currency,
                               BigDecimal amount, BigDecimal rate) {
        jdbcTemplate.update("INSERT INTO expense (user_id, trip_id, country_code, city_code, currency, amount, "
                        + "exchange_rate_snapshot, category, expense_date, deleted) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, 'FOOD', ?, 0)",
                user.getId(), tripId, country, city, currency, amount, rate, LocalDate.now());
    }

    private long lastId(String sql, Object arg) {
        Long id = jdbcTemplate.queryForObject(sql, Long.class, arg);
        return id == null ? 0 : id;
    }
}
