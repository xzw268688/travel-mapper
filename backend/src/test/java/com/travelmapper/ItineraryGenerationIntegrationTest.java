package com.travelmapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.travelmapper.repository.entity.User;
import com.travelmapper.service.config.ConfigEntry;
import com.travelmapper.service.config.ConfigKeys;
import com.travelmapper.service.itinerary.ItineraryGenerationService;
import com.travelmapper.service.llm.LlmProviderRegistry;
import com.travelmapper.service.search.SearchProvider;
import com.travelmapper.service.search.SearchProviderHolder;
import com.travelmapper.support.AuthTestSupport;
import com.travelmapper.support.FakeLlmProvider;
import com.travelmapper.support.FakeSearchProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(ItineraryGenerationIntegrationTest.AiTestConfig.class)
class ItineraryGenerationIntegrationTest extends AuthTestSupport {

    @TestConfiguration
    static class AiTestConfig {
        @Bean
        FakeLlmProvider fakeLlmProvider() {
            return new FakeLlmProvider();
        }

        @Bean
        SearchProvider fakeSearchProvider() {
            return new FakeSearchProvider();
        }
    }

    @Autowired
    private FakeLlmProvider fakeLlm;

    @Autowired
    private FakeSearchProvider fakeSearch;

    @Autowired
    private LlmProviderRegistry llmProviderRegistry;

    @Autowired
    private SearchProviderHolder searchProviderHolder;

    @BeforeEach
    void configureProviders() {
        fakeLlm.reset();
        fakeSearch.reset();
        clearKeys("guides:*");
        clearKeys("prep:*");
        clearKeys("quota:dedupe:*");
        clearKeys("quota:dedupe:lock:*");
        configService.updateGroup(Map.of(
                ConfigKeys.LLM_PROVIDER, ConfigEntry.plain("fake", "test"),
                ConfigKeys.SEARCH_PROVIDER, ConfigEntry.plain("fake", "test"),
                ConfigKeys.SEARCH_TIMEOUT_SECONDS, ConfigEntry.plain("5", "test"),
                ConfigKeys.SEARCH_DAILY_LIMIT, ConfigEntry.plain("50", "test")), 1L);
        llmProviderRegistry.reload();
        searchProviderHolder.reload();
    }

    @Test
    void generatesDraftAndDedupesWithoutDoubleCharging() throws Exception {
        User user = createVerifiedUser("gen");
        String firstId = UUID.randomUUID().toString();
        MvcResult first = mockMvc.perform(withToken(post("/api/itinerary/generate"), user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(firstId, "JP", List.of("TYO", "OSA"), 3, "美食")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.days.length()").value(3))
                .andExpect(jsonPath("$.data.guides.length()").value(3))
                .andExpect(jsonPath("$.data.preparations.visa").value(""))
                .andExpect(jsonPath("$.data.preparations.notes.length()").value(0))
                .andExpect(jsonPath("$.data.quota.source").value("FREE"))
                .andExpect(jsonPath("$.data.quota.freeUsedToday").value(1))
                .andExpect(jsonPath("$.data.deduplicated").value(false))
                .andReturn();
        JsonNode firstData = objectMapper.readTree(first.getResponse().getContentAsString()).path("data");
        assertThat(firstData.path("warnings").size()).isZero();
        assertThat(firstData.path("days").get(0).path("cityCode").asText()).isEqualTo("TYO");
        assertThat(firstData.path("days").get(1).path("cityCode").asText()).isEqualTo("OSA");
        assertThat(quotaService.getSummary(user.getId()).freeUsedToday()).isEqualTo(1);
        assertThat(count("itinerary", "user_id = ?", user.getId())).isZero();

        mockMvc.perform(withToken(post("/api/itinerary/generate"), user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(UUID.randomUUID().toString(), "JP", List.of("TYO", "OSA"), 3, "美食")))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Dedup", "hit"))
                .andExpect(jsonPath("$.data.deduplicated").value(true))
                .andExpect(jsonPath("$.data.quota.freeUsedToday").value(1));
        assertThat(quotaService.getSummary(user.getId()).freeUsedToday()).isEqualTo(1);
    }

    @Test
    void exhaustedQuotaBlocksGenerationBeforeLlm() throws Exception {
        User user = createVerifiedUser("genquota");
        quotaService.checkAndDeduct(user.getId(), UUID.randomUUID().toString());
        quotaService.checkAndDeduct(user.getId(), UUID.randomUUID().toString());
        quotaService.checkAndDeduct(user.getId(), UUID.randomUUID().toString());
        fakeLlm.reset();

        mockMvc.perform(withToken(post("/api/itinerary/generate"), user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(UUID.randomUUID().toString(), "JP", List.of("TYO"), 2, "")))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value(2011));
        assertThat(fakeLlm.calls()).isZero();
        assertThat(count("itinerary", "user_id = ?", user.getId())).isZero();
    }

    @Test
    void retriesInvalidJsonOnceAndSucceeds() throws Exception {
        User user = createVerifiedUser("genretry");
        fakeLlm.setFailFirst(true);
        mockMvc.perform(withToken(post("/api/itinerary/generate"), user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(UUID.randomUUID().toString(), "JP", List.of("TYO"), 2, "")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.days.length()").value(2));
        assertThat(fakeLlm.calls()).isEqualTo(2);
    }

    @Test
    void invalidMultiCityFallsBackToFirstCityWithWarning() throws Exception {
        User user = createVerifiedUser("genfallback");
        fakeLlm.setInvalidMultiCity(true);
        MvcResult result = mockMvc.perform(withToken(post("/api/itinerary/generate"), user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(UUID.randomUUID().toString(), "JP", List.of("TYO", "OSA"), 3, "")))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data = objectMapper.readTree(
                result.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("data");
        assertThat(data.path("days").get(0).path("cityCode").asText()).isEqualTo("TYO");
        assertThat(data.path("days").get(1).path("cityCode").asText()).isEqualTo("TYO");
        assertThat(data.path("warnings").get(0).asText()).contains("多城市");
        assertThat(fakeLlm.calls()).isEqualTo(3);
        assertThat(quotaService.getSummary(user.getId()).freeUsedToday()).isEqualTo(1);
    }

    @Test
    void llmFailureRefundsQuotaAndReturns2012() throws Exception {
        User user = createVerifiedUser("genfail");
        fakeLlm.setAlwaysFail(true);
        mockMvc.perform(withToken(post("/api/itinerary/generate"), user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(UUID.randomUUID().toString(), "JP", List.of("TYO"), 2, "")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(2012));
        assertThat(quotaService.getSummary(user.getId()).freeUsedToday()).isZero();
        assertThat(count("itinerary", "user_id = ?", user.getId())).isZero();
    }

    @Test
    void searchTimeoutDegradesWithoutRefund() throws Exception {
        User user = createVerifiedUser("gensearch");
        fakeSearch.setSlow(true);
        configService.updateGroup(Map.of(
                ConfigKeys.SEARCH_TIMEOUT_SECONDS, ConfigEntry.plain("1", "test")), 1L);
        searchProviderHolder.reload();

        mockMvc.perform(withToken(post("/api/itinerary/generate"), user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(UUID.randomUUID().toString(), "JP", List.of("TYO"), 2, "")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.guides.length()").value(0));
        assertThat(quotaService.getSummary(user.getId()).freeUsedToday()).isEqualTo(1);
    }

    @Test
    void searchDailyLimitReturnsEmptyGuides() throws Exception {
        User user = createVerifiedUser("genlimit");
        configService.updateGroup(Map.of(
                ConfigKeys.SEARCH_DAILY_LIMIT, ConfigEntry.plain("0", "test")), 1L);
        mockMvc.perform(withToken(post("/api/itinerary/generate"), user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(UUID.randomUUID().toString(), "JP", List.of("TYO"), 2, "限流")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.guides.length()").value(0));
    }

    @Test
    void guidesCachesAreReusedAcrossUsersWithoutPreparations() throws Exception {
        User first = createVerifiedUser("gencache1");
        mockMvc.perform(withToken(post("/api/itinerary/generate"), first)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(UUID.randomUUID().toString(), "JP", List.of("TYO", "OSA"), 3, "美食")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.preparations.visa").value(""));
        assertThat(fakeSearch.calls()).isEqualTo(1);

        User second = createVerifiedUser("gencache2");
        mockMvc.perform(withToken(post("/api/itinerary/generate"), second)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(UUID.randomUUID().toString(), "JP", List.of("TYO", "OSA"), 5, "美食")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.preparations.visa").value(""));
        assertThat(fakeLlm.calls()).isEqualTo(2);
        assertThat(fakeSearch.calls()).isEqualTo(2);

        User third = createVerifiedUser("gencache3");
        mockMvc.perform(withToken(post("/api/itinerary/generate"), third)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(UUID.randomUUID().toString(), "JP", List.of("TYO", "OSA"), 3, "美食")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.preparations.visa").value(""))
                .andExpect(jsonPath("$.data.guides.length()").value(3));
        assertThat(fakeLlm.calls()).isEqualTo(3);
        assertThat(fakeSearch.calls()).isEqualTo(2);
        assertThat(redis.keys("prep:*")).isEmpty();
    }

    @Test
    void inFlightLockReturns3001AndInvalidInputsAreRejected() throws Exception {
        User user = createVerifiedUser("genlock");
        String fingerprint = ItineraryGenerationService.fingerprint(
                "JP", List.of("OSA", "TYO"), 3, "美食");
        redis.opsForValue().set("quota:dedupe:lock:" + user.getId() + ":" + fingerprint,
                "1", java.time.Duration.ofMinutes(2));
        mockMvc.perform(withToken(post("/api/itinerary/generate"), user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(UUID.randomUUID().toString(), "JP", List.of("TYO", "OSA"), 3, "美食")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(3001));
        assertThat(fakeLlm.calls()).isZero();

        User invalid = createVerifiedUser("geninvalid");
        mockMvc.perform(withToken(post("/api/itinerary/generate"), invalid)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(UUID.randomUUID().toString(), "JP", List.of("NOPE"), 2, "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(2002));
        mockMvc.perform(withToken(post("/api/itinerary/generate"), invalid)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(UUID.randomUUID().toString(), "JP", List.of("TYO"), 2, "" )
                                .replace(LocalDate.now().plusDays(1).toString(),
                                        LocalDate.now().minusDays(2).toString())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(2001));
        mockMvc.perform(withToken(post("/api/itinerary/generate"), invalid)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("not-a-uuid", "JP", List.of("TYO"), 2, "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(2001));
    }

    private String body(String requestId, String countryCode, List<String> cities,
                        int days, String preference) {
        return json(Map.of(
                "requestId", requestId,
                "countryCode", countryCode,
                "cityCodes", cities,
                "days", days,
                "startDate", LocalDate.now().plusDays(1).toString(),
                "preference", preference));
    }

    private void clearKeys(String pattern) {
        Set<String> keys = redis.keys(pattern);
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
        }
    }
}
