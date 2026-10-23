package com.travelmapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.travelmapper.repository.entity.User;
import com.travelmapper.support.AiTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ItineraryManagementIntegrationTest extends AiTestSupport {

    @Test
    void saveDetailEditDeleteAndValidation() throws Exception {
        User user = createVerifiedUser("itinsave");
        LocalDate start = LocalDate.now().plusDays(1);
        List<Map<String, Object>> days = List.of(
                day(1, start, "TYO", List.of(spot("浅草寺", "AI"))),
                day(2, start.plusDays(1), "OSA",
                        List.of(spot("大阪城", "AI"), spot("咖啡店", "MANUAL"))),
                day(3, start.plusDays(2), "TYO", List.of(spot("银座", "AI"))));

        MvcResult saved = mockMvc.perform(withToken(post("/api/itinerary/save"), user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(saveBody("东京大阪 3 日", start, days))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tripCreated").value(true))
                .andExpect(jsonPath("$.data.countryStatusAfter").value("YELLOW"))
                .andExpect(jsonPath("$.data.manualSpotCount").value(1))
                .andReturn();
        long itineraryId = data(saved).path("itineraryId").asLong();
        long tripId = data(saved).path("tripId").asLong();
        assertThat(tripId).isPositive();

        mockMvc.perform(withToken(get("/api/itinerary/" + itineraryId), user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.days.length()").value(3))
                .andExpect(jsonPath("$.data.days[1].spots.length()").value(2))
                .andExpect(jsonPath("$.data.days[0].cityName").value("东京"))
                .andExpect(jsonPath("$.data.days[1].cityName").value("大阪"))
                .andExpect(jsonPath("$.data.manualSpotCount").value(1))
                .andExpect(jsonPath("$.data.preparations.visa").value("v"))
                .andExpect(jsonPath("$.data.tripName").isString());

        assertThat(count("itinerary", "user_id = ?", user.getId())).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM itinerary_day WHERE itinerary_id = ?", Integer.class, itineraryId))
                .isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM itinerary_spot s JOIN itinerary_day d ON s.day_id = d.id "
                        + "WHERE d.itinerary_id = ?", Integer.class, itineraryId)).isEqualTo(4);
        assertThat(count("trip", "user_id = ?", user.getId())).isEqualTo(1);

        LocalDate newStart = start.plusDays(1);
        mockMvc.perform(withToken(put("/api/itinerary/" + itineraryId), user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("title", "新标题", "startDate", newStart.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("新标题"))
                .andExpect(jsonPath("$.data.startDate").value(newStart.toString()))
                .andExpect(jsonPath("$.data.days[0].date").value(newStart.toString()));

        mockMvc.perform(withToken(get("/api/map/stats"), user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.countries[?(@.countryCode == 'JP')].status")
                        .value(org.hamcrest.Matchers.hasItem("YELLOW")));

        mockMvc.perform(withToken(delete("/api/itinerary/" + itineraryId), user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.countryStatusAfter").value("GRAY"));
        mockMvc.perform(withToken(get("/api/itinerary/" + itineraryId), user))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(2003));
        User other = createVerifiedUser("itinsave2");
        mockMvc.perform(withToken(get("/api/itinerary/" + itineraryId), other))
                .andExpect(status().isNotFound());
        mockMvc.perform(withToken(get("/api/trips").param("countryCode", "JP"), user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].itineraryCount").value(0));

        User invalid = createVerifiedUser("itinvalid");
        mockMvc.perform(withToken(post("/api/itinerary/save"), invalid)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("countryCode", "JP", "title", "x",
                                "startDate", start.plusDays(2).toString(),
                                "endDate", start.toString(), "days", List.of(day(1, start, "TYO", List.of()))))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(2001));
        mockMvc.perform(withToken(post("/api/itinerary/save"), invalid)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("countryCode", "JP", "title", "x",
                                "startDate", start.toString(), "endDate", start.plusDays(2).toString(),
                                "days", List.of(day(1, start, "TYO", List.of()),
                                        day(2, start.plusDays(1), "TYO", List.of()))))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(2001));
        mockMvc.perform(withToken(post("/api/itinerary/save"), invalid)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(saveBody("非法城市", start,
                                List.of(day(1, start, "NOPE", List.of()))))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(2002));

        List<Map<String, Object>> manyManual = new ArrayList<>();
        for (int i = 1; i <= 4; i++) {
            List<Map<String, Object>> spots = new ArrayList<>();
            int count = i < 4 ? 8 : 7;
            for (int j = 0; j < count; j++) {
                spots.add(spot("手动" + i + "-" + j, "MANUAL"));
            }
            manyManual.add(day(i, start.plusDays(i - 1), "TYO", spots));
        }
        mockMvc.perform(withToken(post("/api/itinerary/save"), invalid)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(saveBody("超限", start, manyManual))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(2006));
    }

    @Test
    void spotCrudMoveAndLimit() throws Exception {
        User user = createVerifiedUser("spots");
        LocalDate start = LocalDate.now().plusDays(1);
        long itineraryId = save(user, "景点测试", start,
                List.of(day(1, start, "TYO", List.of()),
                        day(2, start.plusDays(1), "TYO", List.of())));
        JsonNode detail = detail(user, itineraryId);
        long day1 = detail.path("days").get(0).path("dayId").asLong();
        long day2 = detail.path("days").get(1).path("dayId").asLong();

        MvcResult added = mockMvc.perform(withToken(post("/api/itinerary/" + itineraryId
                        + "/days/" + day1 + "/spots"), user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"私藏店\",\"description\":\"好\",\"suggestedDuration\":\"1小时\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.manualSpotCount").value(1))
                .andReturn();
        long spot1 = data(added).path("spotId").asLong();

        mockMvc.perform(withToken(put("/api/itinerary/spots/" + spot1), user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"表参道咖啡\",\"description\":\"好\",\"suggestedDuration\":\"1.5小时\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.source").value("MANUAL"))
                .andExpect(jsonPath("$.data.name").value("表参道咖啡"));

        MvcResult second = mockMvc.perform(withToken(post("/api/itinerary/" + itineraryId
                        + "/days/" + day2 + "/spots"), user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"第二景点\"}"))
                .andExpect(status().isOk()).andReturn();
        long spot2 = data(second).path("spotId").asLong();

        mockMvc.perform(withToken(put("/api/itinerary/spots/" + spot1 + "/move"), user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("targetDayId", day2, "targetIndex", 0))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.affectedDays.length()").value(2));
        JsonNode moved = detail(user, itineraryId);
        assertThat(moved.path("days").get(1).path("spots").size()).isEqualTo(2);

        mockMvc.perform(withToken(delete("/api/itinerary/spots/" + spot1), user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.manualSpotCount").value(1));

        for (int i = 0; i < 29; i++) {
            jdbcTemplate.update("INSERT INTO itinerary_spot (day_id, sort_order, source, name) "
                    + "VALUES (?, ?, 'MANUAL', ?)", day1, i, "填充" + i);
        }
        mockMvc.perform(withToken(post("/api/itinerary/" + itineraryId + "/days/" + day1 + "/spots"), user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"超限景点\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(2006));

        User other = createVerifiedUser("spots2");
        mockMvc.perform(withToken(put("/api/itinerary/spots/" + spot2), other)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"越权\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void dayManagementKeepsDatesAndSpotsConsistent() throws Exception {
        User user = createVerifiedUser("days");
        LocalDate start = LocalDate.now().plusDays(1);
        long itineraryId = save(user, "天数测试", start,
                List.of(day(1, start, "TYO", List.of(spot("景点1", "AI"))),
                        day(2, start.plusDays(1), "TYO", List.of(spot("景点2", "AI"))),
                        day(3, start.plusDays(2), "TYO", List.of(spot("景点3", "AI")))));

        mockMvc.perform(withToken(post("/api/itinerary/" + itineraryId + "/days"), user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("insertIndex", 2,
                                "date", start.plusDays(1).toString(), "cityCode", "OSA"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.days.length()").value(4))
                .andExpect(jsonPath("$.data.days[1].cityCode").value("OSA"));

        JsonNode detail = detail(user, itineraryId);
        long newDayId = detail.path("days").get(1).path("dayId").asLong();
        mockMvc.perform(withToken(put("/api/itinerary/" + itineraryId + "/days/" + newDayId), user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("date", start.plusDays(1).toString(), "cityCode", "KYO"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.days[1].cityCode").value("KYO"));

        mockMvc.perform(withToken(delete("/api/itinerary/" + itineraryId + "/days/" + newDayId), user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dayCount").value(3))
                .andExpect(jsonPath("$.data.deletedSpots").value(0));
        assertThat(detail(user, itineraryId).path("days").size()).isEqualTo(3);

        long single = save(user, "单天", start, List.of(day(1, start, "TYO", List.of())));
        long singleDay = detail(user, single).path("days").get(0).path("dayId").asLong();
        mockMvc.perform(withToken(delete("/api/itinerary/" + single + "/days/" + singleDay), user))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(2001));
        assertThat(quotaService.getSummary(user.getId()).freeUsedToday()).isZero();
    }

    @Test
    void regenerateMergesManualSpotsAndRespectsLimit() throws Exception {
        User user = createVerifiedUser("regen");
        LocalDate start = LocalDate.now().plusDays(1);
        long itineraryId = save(user, "重新生成", start,
                List.of(day(1, start, "TYO", List.of()),
                        day(2, start.plusDays(1), "TYO", List.of()),
                        day(3, start.plusDays(2), "OSA", List.of())));
        JsonNode detail = detail(user, itineraryId);
        long day2 = detail.path("days").get(1).path("dayId").asLong();
        mockMvc.perform(withToken(post("/api/itinerary/" + itineraryId + "/days/" + day2 + "/spots"), user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"保留咖啡店\"}"))
                .andExpect(status().isOk());

        fakeLlm.reset();
        mockMvc.perform(withToken(post("/api/itinerary/" + itineraryId + "/regenerate"), user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("requestId", UUID.randomUUID().toString(),
                                "keepManualSpots", true, "preference", "慢"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mergedManualSpots").value(1))
                .andExpect(jsonPath("$.data.mergeWarnings[0]").isString())
                .andExpect(jsonPath("$.data.itinerary.days.length()").value(3));
        JsonNode regenerated = detail(user, itineraryId);
        boolean manualKept = false;
        for (JsonNode day : regenerated.path("days")) {
            for (JsonNode spot : day.path("spots")) {
                if ("保留咖啡店".equals(spot.path("name").asText())) {
                    manualKept = true;
                    assertThat(spot.path("source").asText()).isEqualTo("MANUAL");
                }
            }
        }
        assertThat(manualKept).isTrue();
        assertThat(quotaService.getSummary(user.getId()).freeUsedToday()).isEqualTo(1);

        mockMvc.perform(withToken(post("/api/itinerary/" + itineraryId + "/regenerate"), user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("requestId", UUID.randomUUID().toString(),
                                "keepManualSpots", false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mergedManualSpots").value(0));
        assertThat(detail(user, itineraryId).path("manualSpotCount").asInt()).isZero();

        long dayOne = detail(user, itineraryId).path("days").get(0).path("dayId").asLong();
        for (int i = 0; i < 30; i++) {
            jdbcTemplate.update("INSERT INTO itinerary_spot (day_id, sort_order, source, name) "
                    + "VALUES (?, ?, 'MANUAL', ?)", dayOne, i, "保留" + i);
        }
        fakeLlm.reset();
        mockMvc.perform(withToken(post("/api/itinerary/" + itineraryId + "/regenerate"), user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("requestId", UUID.randomUUID().toString(),
                                "keepManualSpots", true))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(2006));
        assertThat(fakeLlm.calls()).isZero();
        assertThat(quotaService.getSummary(user.getId()).freeUsedToday()).isEqualTo(2);
    }

    @Test
    void generateSaveAndLightMapEndToEnd() throws Exception {
        User user = createVerifiedUser("e2e");
        LocalDate start = LocalDate.now().plusDays(1);
        MvcResult generated = mockMvc.perform(withToken(post("/api/itinerary/generate"), user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "requestId", UUID.randomUUID().toString(),
                                "countryCode", "JP",
                                "cityCodes", List.of("TYO", "OSA"),
                                "days", 2,
                                "startDate", start.toString(),
                                "preference", "美食"))))
                .andExpect(status().isOk()).andReturn();
        JsonNode draft = data(generated);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("countryCode", draft.path("countryCode").asText());
        body.put("title", "E2E 行程");
        body.put("startDate", draft.path("startDate").asText());
        body.put("endDate", draft.path("endDate").asText());
        body.put("guides", objectMapper.convertValue(draft.path("guides"), List.class));
        body.put("preparations", objectMapper.convertValue(draft.path("preparations"), Map.class));
        body.put("days", objectMapper.convertValue(draft.path("days"), List.class));

        mockMvc.perform(withToken(post("/api/itinerary/save"), user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tripCreated").value(true))
                .andExpect(jsonPath("$.data.countryStatusAfter").value("YELLOW"));
        assertThat(count("itinerary", "user_id = ?", user.getId())).isEqualTo(1);
        assertThat(count("trip", "user_id = ?", user.getId())).isEqualTo(1);
        assertThat(quotaService.getSummary(user.getId()).freeUsedToday()).isEqualTo(1);
        mockMvc.perform(withToken(get("/api/map/stats"), user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.countries[?(@.countryCode == 'JP')].status")
                        .value(org.hamcrest.Matchers.hasItem("YELLOW")));
    }

    @Test
    void checklistAndOfficialLinks() throws Exception {
        User user = createVerifiedUser("prep");
        LocalDate start = LocalDate.now().plusDays(1);
        long itineraryId = save(user, "行前", start,
                List.of(day(1, start, "TYO", List.of()), day(2, start.plusDays(1), "TYO", List.of())));

        mockMvc.perform(withToken(get("/api/prep/checklist").param("itineraryId", String.valueOf(itineraryId)), user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(7))
                .andExpect(jsonPath("$.data.items[0].itemKey").value("VISA"))
                .andExpect(jsonPath("$.data.items[0].checked").value(false));
        mockMvc.perform(withToken(put("/api/prep/checklist"), user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("itineraryId", itineraryId, "itemKey", "VISA", "checked", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.checked").value(true));
        mockMvc.perform(withToken(get("/api/prep/checklist").param("itineraryId", String.valueOf(itineraryId)), user))
                .andExpect(jsonPath("$.data.items[0].checked").value(true));
        mockMvc.perform(withToken(put("/api/prep/checklist"), user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("itineraryId", itineraryId, "itemKey", "NOPE", "checked", true))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(2022));
        User other = createVerifiedUser("prep2");
        mockMvc.perform(withToken(get("/api/prep/checklist").param("itineraryId", String.valueOf(itineraryId)), other))
                .andExpect(status().isNotFound());
        assertThat(quotaService.getSummary(user.getId()).freeUsedToday()).isZero();

        jdbcTemplate.update("DELETE FROM country_official_link WHERE country_code = 'JP'");
        mockMvc.perform(withToken(get("/api/prep/official-links").param("countryCode", "JP"), user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fallback").value(true))
                .andExpect(jsonPath("$.data.links").isEmpty())
                .andExpect(jsonPath("$.data.searchUrl").value(org.hamcrest.Matchers.containsString("bing.com/search")));

        Long firstId = null;
        for (int i = 1; i <= 3; i++) {
            MvcResult created = mockMvc.perform(asAdmin(post("/api/admin/official-links"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("countryCode", "JP", "title", "链接" + i,
                                    "url", "https://example.com/" + i, "sortOrder", i))))
                    .andExpect(status().isOk()).andReturn();
            if (i == 1) {
                firstId = data(created).path("id").asLong();
            }
        }
        mockMvc.perform(asAdmin(post("/api/admin/official-links"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("countryCode", "JP", "title", "第四条",
                                "url", "https://example.com/4"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(2026));
        mockMvc.perform(asAdmin(post("/api/admin/official-links"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("countryCode", "TH", "title", "非法",
                                "url", "ftp://example.com"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(2001));
        mockMvc.perform(withToken(get("/api/prep/official-links").param("countryCode", "JP"), user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fallback").value(false))
                .andExpect(jsonPath("$.data.links.length()").value(3));

        mockMvc.perform(asAdmin(put("/api/admin/official-links/" + firstId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("countryCode", "JP", "title", "更新",
                                "url", "https://example.com/new"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("更新"));
        mockMvc.perform(asAdmin(delete("/api/admin/official-links/" + firstId)))
                .andExpect(status().isOk());
        mockMvc.perform(withToken(get("/api/admin/official-links"), user))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(4002));
    }

    private long save(User user, String title, LocalDate start,
                      List<Map<String, Object>> days) throws Exception {
        MvcResult result = mockMvc.perform(withToken(post("/api/itinerary/save"), user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(saveBody(title, start, days))))
                .andExpect(status().isOk()).andReturn();
        return data(result).path("itineraryId").asLong();
    }

    private Map<String, Object> saveBody(String title, LocalDate start,
                                         List<Map<String, Object>> days) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("countryCode", "JP");
        body.put("title", title);
        body.put("startDate", start.toString());
        body.put("endDate", start.plusDays(days.size() - 1L).toString());
        body.put("guides", List.of());
        body.put("preparations", Map.of("visa", "v", "currency", "c",
                "powerPlug", "p", "bestSeason", "s", "notes", List.of("n")));
        body.put("days", days);
        return body;
    }

    private Map<String, Object> day(int index, LocalDate date, String city,
                                    List<Map<String, Object>> spots) {
        Map<String, Object> day = new LinkedHashMap<>();
        day.put("dayIndex", index);
        day.put("date", date.toString());
        day.put("cityCode", city);
        day.put("spots", spots);
        return day;
    }

    private Map<String, Object> spot(String name, String source) {
        Map<String, Object> spot = new LinkedHashMap<>();
        spot.put("name", name);
        spot.put("description", "描述");
        spot.put("suggestedDuration", "2小时");
        spot.put("source", source);
        return spot;
    }

    private JsonNode detail(User user, long itineraryId) throws Exception {
        MvcResult result = mockMvc.perform(withToken(get("/api/itinerary/" + itineraryId), user))
                .andExpect(status().isOk()).andReturn();
        return data(result);
    }

    private JsonNode data(MvcResult result) throws Exception {
        return objectMapper.readTree(
                result.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("data");
    }
}
