package com.travelmapper;

import com.travelmapper.repository.entity.User;
import com.travelmapper.support.AiTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CountryGuideIntegrationTest extends AiTestSupport {

    @Test
    void firstGenerationStreamsThreeSectionsAndCachesForOneDay() throws Exception {
        User user = createVerifiedUser("guide");
        String body = streamGuide(user, UUID.randomUUID().toString(), "JP");

        assertThat(body).contains("event:meta", "event:delta", "event:done",
                "\"cached\":false", "\"quotaSource\":\"FREE\"",
                "日本是位于东亚", "\"visa\":\"需提前办理签证\"", "\"attractions\":[");
        assertThat(quotaService.getSummary(user.getId()).freeUsedToday()).isEqualTo(1);
        assertThat(fakeLlm.guideCalls()).isEqualTo(1);

        Long ttl = redis.getExpire("country-guide:v1:JP", TimeUnit.SECONDS);
        assertThat(ttl).isBetween(Duration.ofHours(23).toSeconds(), Duration.ofHours(24).toSeconds());
    }

    @Test
    void secondUserReadsSharedCacheWithoutCharge() throws Exception {
        User first = createVerifiedUser("guidefirst");
        User second = createVerifiedUser("guidsecond");
        streamGuide(first, UUID.randomUUID().toString(), "JP");
        int callsAfterFirst = fakeLlm.guideCalls();

        mockMvc.perform(withToken(get("/api/country-guide/JP"), second))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.available").value(true))
                .andExpect(jsonPath("$.data.guide.cached").value(true))
                .andExpect(jsonPath("$.data.guide.intro").value(containsString("日本")))
                .andExpect(jsonPath("$.data.guide.notice.currency").value("日元，现金仍较常用"));

        String body = streamGuide(second, UUID.randomUUID().toString(), "JP");
        assertThat(body).contains("event:done", "\"quotaSource\":\"CACHE\"", "\"cached\":true")
                .doesNotContain("event:delta");
        assertThat(quotaService.getSummary(second.getId()).freeUsedToday()).isZero();
        assertThat(fakeLlm.guideCalls()).isEqualTo(callsAfterFirst);
    }

    @Test
    void cacheReadReportsUnavailableWhenEmpty() throws Exception {
        User user = createVerifiedUser("guideempty");
        mockMvc.perform(withToken(get("/api/country-guide/FR"), user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.available").value(false))
                .andExpect(jsonPath("$.data.guide").doesNotExist());
        assertThat(quotaService.getSummary(user.getId()).freeUsedToday()).isZero();
    }

    @Test
    void validatesRequestCountryAndAuthentication() throws Exception {
        User user = createVerifiedUser("guidevalid");
        mockMvc.perform(withToken(post("/api/country-guide/stream"), user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("requestId", "not-a-uuid", "countryCode", "JP"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(2001));

        mockMvc.perform(withToken(post("/api/country-guide/stream"), user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("requestId", UUID.randomUUID().toString(),
                                "countryCode", "ZZ"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(2002));

        mockMvc.perform(withToken(get("/api/country-guide/ZZ"), user))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(2002));

        mockMvc.perform(post("/api/country-guide/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("requestId", UUID.randomUUID().toString(),
                                "countryCode", "JP"))))
                .andExpect(status().isUnauthorized());

        assertThat(quotaService.getSummary(user.getId()).freeUsedToday()).isZero();
    }

    @Test
    void exhaustedQuotaRejectsBeforeLlm() throws Exception {
        User user = createVerifiedUser("guidequota");
        quotaService.checkAndDeduct(user.getId(), UUID.randomUUID().toString());
        quotaService.checkAndDeduct(user.getId(), UUID.randomUUID().toString());
        quotaService.checkAndDeduct(user.getId(), UUID.randomUUID().toString());

        mockMvc.perform(withToken(post("/api/country-guide/stream"), user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("requestId", UUID.randomUUID().toString(),
                                "countryCode", "JP"))))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value(2011));
        assertThat(fakeLlm.guideCalls()).isZero();
    }

    @Test
    void failureBeforeOutputRefundsQuota() throws Exception {
        User user = createVerifiedUser("guidefail");
        fakeLlm.setFailGuide(true);
        String body = streamGuide(user, UUID.randomUUID().toString(), "JP");
        assertThat(body).contains("event:error").doesNotContain("event:done");
        assertThat(quotaService.getSummary(user.getId()).freeUsedToday()).isZero();
    }

    @Test
    void failureAfterPartialOutputKeepsCharge() throws Exception {
        User user = createVerifiedUser("guidepartial");
        fakeLlm.setFailGuideAfterOutput(true);
        String body = streamGuide(user, UUID.randomUUID().toString(), "JP");
        assertThat(body).contains("event:delta", "event:error").doesNotContain("event:done");
        assertThat(quotaService.getSummary(user.getId()).freeUsedToday()).isEqualTo(1);
    }

    @Test
    void emptyModelOutputIsTreatedAsFailure() throws Exception {
        User user = createVerifiedUser("guideblank");
        fakeLlm.setGuideOutput("");
        String body = streamGuide(user, UUID.randomUUID().toString(), "JP");
        assertThat(body).contains("event:error");
        assertThat(quotaService.getSummary(user.getId()).freeUsedToday()).isZero();
        assertThat(redis.hasKey("country-guide:v1:JP")).isFalse();
    }

    @Test
    void concurrentGenerationReturnsDuplicateRequest() throws Exception {
        User user = createVerifiedUser("guidelock");
        redis.opsForValue().set("country-guide:lock:JP", "other-request", Duration.ofMinutes(1));

        mockMvc.perform(withToken(post("/api/country-guide/stream"), user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("requestId", UUID.randomUUID().toString(),
                                "countryCode", "JP"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(3001));
        assertThat(quotaService.getSummary(user.getId()).freeUsedToday()).isZero();
    }

    private String streamGuide(User user, String requestId, String countryCode) throws Exception {
        MvcResult result = mockMvc.perform(withToken(post("/api/country-guide/stream"), user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("requestId", requestId, "countryCode", countryCode))))
                .andExpect(request().asyncStarted())
                .andReturn();
        mockMvc.perform(asyncDispatch(result)).andExpect(status().isOk());
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }
}