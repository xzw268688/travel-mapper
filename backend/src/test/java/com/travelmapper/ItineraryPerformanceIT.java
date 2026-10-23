package com.travelmapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.travelmapper.repository.entity.User;
import com.travelmapper.service.config.ConfigEntry;
import com.travelmapper.service.config.ConfigKeys;
import com.travelmapper.support.AiTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * change-04 10.5 性能基准（MockMvc 层，不含真实网络与 LLM 延迟）。
 * 默认不执行：mvn -Dtest=ItineraryPerformanceIT -Dperf.enabled=true test
 */
@EnabledIfSystemProperty(named = "perf.enabled", matches = "true")
class ItineraryPerformanceIT extends AiTestSupport {

    private static final int ITERATIONS = Integer.getInteger("perf.iterations", 100);
    private static final int WARMUP = Integer.getInteger("perf.warmup", 10);
    private static final double NORMAL_P95_MS = 500;
    private static final double MANUAL_SPOT_P95_MS = 300;

    private User user;
    private long itineraryId;
    private long day1Id;
    private long day2Id;
    private LocalDate start;

    @BeforeEach
    void setUp() throws Exception {
        user = createVerifiedUser("itperf");
        jdbcTemplate.update(
                "UPDATE `user` SET vip_expire_at = DATE_ADD(NOW(), INTERVAL 365 DAY) WHERE id = ?",
                user.getId());
        configService.updateGroup(Map.of(
                ConfigKeys.QUOTA_VIP_DAILY, ConfigEntry.plain("100000", "perf")), 1L);
        start = LocalDate.now().plusDays(1);
        JsonNode saved = data(mockMvc.perform(withToken(post("/api/itinerary/save"), user)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(saveBody(unique("perf base"), start)))));
        itineraryId = saved.path("itineraryId").asLong();
        JsonNode days = data(mockMvc.perform(withToken(get("/api/itinerary/" + itineraryId), user)))
                .path("days");
        day1Id = days.get(0).path("dayId").asLong();
        day2Id = days.get(1).path("dayId").asLong();
    }

    @AfterEach
    void cleanUp() {
        long uid = user.getId();
        jdbcTemplate.update("DELETE FROM trip_checklist WHERE user_id = ?", uid);
        jdbcTemplate.update("DELETE FROM quota_ledger WHERE user_id = ?", uid);
        jdbcTemplate.update("DELETE FROM quota_pack WHERE user_id = ?", uid);
        jdbcTemplate.update("DELETE s FROM itinerary_spot s "
                + "JOIN itinerary_day d ON s.day_id = d.id "
                + "JOIN itinerary i ON d.itinerary_id = i.id WHERE i.user_id = ?", uid);
        jdbcTemplate.update("DELETE d FROM itinerary_day d "
                + "JOIN itinerary i ON d.itinerary_id = i.id WHERE i.user_id = ?", uid);
        jdbcTemplate.update("DELETE FROM itinerary WHERE user_id = ?", uid);
        jdbcTemplate.update("DELETE FROM trip WHERE user_id = ?", uid);
        jdbcTemplate.update("DELETE FROM `user` WHERE id = ?", uid);
    }

    @Test
    void itineraryApisMeetLatencyTargets() throws Exception {
        List<Double> generate = benchmark(this::generateOnce);
        List<Double> save = benchmark(this::saveOnce);
        List<Double> addSpot = benchmark(this::addSpotOnce);
        List<Double> updateSpot = benchmark(this::updateSpotOnce);
        List<Double> moveSpot = benchmark(this::moveSpotOnce);
        List<Double> addDay = benchmark(this::addDayOnce);
        List<Double> deleteDay = benchmark(this::deleteDayOnce);
        List<Double> updateDay = benchmark(this::updateDayOnce);
        List<Double> checklistGet = benchmark(this::checklistGetOnce);
        List<Double> checklistPut = benchmark(this::checklistPutOnce);

        report("生成", generate, NORMAL_P95_MS);
        report("保存", save, NORMAL_P95_MS);
        report("手动景点-新增", addSpot, MANUAL_SPOT_P95_MS);
        report("手动景点-编辑", updateSpot, MANUAL_SPOT_P95_MS);
        report("手动景点-移动", moveSpot, MANUAL_SPOT_P95_MS);
        report("天-新增", addDay, NORMAL_P95_MS);
        report("天-修改", updateDay, NORMAL_P95_MS);
        report("天-删除", deleteDay, NORMAL_P95_MS);
        report("清单-读取", checklistGet, NORMAL_P95_MS);
        report("清单-保存", checklistPut, NORMAL_P95_MS);
    }

    // ---------- 场景 ----------

    private void generateOnce() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("requestId", UUID.randomUUID().toString());
        body.put("countryCode", "JP");
        body.put("cityCodes", List.of("TYO"));
        body.put("days", 3);
        body.put("startDate", start.toString());
        // 每次使用不同偏好，避免命中 60 秒结果缓存
        body.put("preference", "perf-" + UUID.randomUUID());
        mockMvc.perform(withToken(post("/api/itinerary/generate"), user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isOk());
    }

    private void saveOnce() throws Exception {
        mockMvc.perform(withToken(post("/api/itinerary/save"), user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(saveBody(unique("perf save"), start))))
                .andExpect(status().isOk());
    }

    private void addSpotOnce() throws Exception {
        JsonNode added = data(mockMvc.perform(withToken(
                post("/api/itinerary/" + itineraryId + "/days/" + day1Id + "/spots"), user)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(spotBody("Perf spot " + UUID.randomUUID())))));
        long spotId = added.path("spotId").asLong();
        // 清理不计时，保证手动景点数量不累积
        mockMvc.perform(withToken(delete("/api/itinerary/spots/" + spotId), user))
                .andExpect(status().isOk());
    }

    private void updateSpotOnce() throws Exception {
        long spotId = addSpotUnmeasured();
        mockMvc.perform(withToken(put("/api/itinerary/spots/" + spotId), user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(spotBody("Updated spot " + UUID.randomUUID()))))
                .andExpect(status().isOk());
        mockMvc.perform(withToken(delete("/api/itinerary/spots/" + spotId), user))
                .andExpect(status().isOk());
    }

    private void moveSpotOnce() throws Exception {
        long spotId = addSpotUnmeasured();
        mockMvc.perform(withToken(put("/api/itinerary/spots/" + spotId + "/move"), user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("targetDayId", day2Id, "targetIndex", 0))))
                .andExpect(status().isOk());
        mockMvc.perform(withToken(delete("/api/itinerary/spots/" + spotId), user))
                .andExpect(status().isOk());
    }

    private void addDayOnce() throws Exception {
        JsonNode detail = data(mockMvc.perform(withToken(post("/api/itinerary/" + itineraryId + "/days"), user)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("insertIndex", 4,
                        "date", start.plusDays(3).toString(), "cityCode", "TYO")))));
        long newDayId = detail.path("days").get(3).path("dayId").asLong();
        // 删除不计时，保持天数为 3
        mockMvc.perform(withToken(
                        delete("/api/itinerary/" + itineraryId + "/days/" + newDayId), user))
                .andExpect(status().isOk());
    }

    private void deleteDayOnce() throws Exception {
        JsonNode detail = data(mockMvc.perform(withToken(post("/api/itinerary/" + itineraryId + "/days"), user)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("insertIndex", 4,
                        "date", start.plusDays(3).toString(), "cityCode", "TYO")))));
        long newDayId = detail.path("days").get(3).path("dayId").asLong();
        mockMvc.perform(withToken(
                        delete("/api/itinerary/" + itineraryId + "/days/" + newDayId), user))
                .andExpect(status().isOk());
    }

    private void updateDayOnce() throws Exception {
        mockMvc.perform(withToken(put("/api/itinerary/" + itineraryId + "/days/" + day2Id), user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("cityCode", "OSA"))))
                .andExpect(status().isOk());
        // 恢复原城市，不计时
        mockMvc.perform(withToken(put("/api/itinerary/" + itineraryId + "/days/" + day2Id), user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("cityCode", "TYO"))))
                .andExpect(status().isOk());
    }

    private void checklistGetOnce() throws Exception {
        mockMvc.perform(withToken(get("/api/prep/checklist"), user)
                        .param("itineraryId", String.valueOf(itineraryId)))
                .andExpect(status().isOk());
    }

    private void checklistPutOnce() throws Exception {
        mockMvc.perform(withToken(put("/api/prep/checklist"), user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("itineraryId", itineraryId, "itemKey", "VISA",
                                "checked", UUID.randomUUID().hashCode() % 2 == 0))))
                .andExpect(status().isOk());
    }

    private long addSpotUnmeasured() throws Exception {
        JsonNode added = data(mockMvc.perform(withToken(
                post("/api/itinerary/" + itineraryId + "/days/" + day1Id + "/spots"), user)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(spotBody("Perf spot " + UUID.randomUUID())))));
        return added.path("spotId").asLong();
    }

    // ---------- 工具 ----------

    private Map<String, Object> saveBody(String title, LocalDate startDate) {
        List<Map<String, Object>> days = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            days.add(Map.of(
                    "dayIndex", i + 1,
                    "date", startDate.plusDays(i).toString(),
                    "cityCode", "TYO",
                    "spots", List.of(spotBody("AI spot"))));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("countryCode", "JP");
        body.put("title", title);
        body.put("startDate", startDate.toString());
        body.put("endDate", startDate.plusDays(2).toString());
        body.put("guides", List.of());
        body.put("preparations", Map.of(
                "visa", "v", "currency", "c", "powerPlug", "p",
                "bestSeason", "s", "notes", List.of("n")));
        body.put("days", days);
        return body;
    }

    private Map<String, Object> spotBody(String name) {
        return Map.of("name", name, "description", "perf",
                "suggestedDuration", "1h", "source", "AI");
    }

    private List<Double> benchmark(ThrowingRunnable action) throws Exception {
        for (int i = 0; i < WARMUP; i++) {
            action.run();
        }
        List<Double> samples = new ArrayList<>();
        for (int i = 0; i < ITERATIONS; i++) {
            long started = System.nanoTime();
            action.run();
            samples.add((System.nanoTime() - started) / 1_000_000.0);
        }
        return samples;
    }

    private void report(String name, List<Double> samples, double threshold) {
        Collections.sort(samples);
        double p50 = samples.get((int) ((samples.size() - 1) * 0.50));
        double p95 = samples.get((int) ((samples.size() - 1) * 0.95));
        double max = samples.get(samples.size() - 1);
        System.out.printf("%-14s p50=%7.1fms p95=%7.1fms max=%7.1fms%n", name, p50, p95, max);
        assertThat(p95)
                .as("%s P95 超过 %.0fms", name, threshold)
                .isLessThan(threshold);
    }

    private JsonNode data(ResultActions actions) throws Exception {
        return objectMapper.readTree(
                actions.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data");
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
