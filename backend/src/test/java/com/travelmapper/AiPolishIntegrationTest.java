package com.travelmapper;

import com.travelmapper.repository.entity.User;
import com.travelmapper.support.AiTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AiPolishIntegrationTest extends AiTestSupport {

    @Test
    void streamsMetaDeltasAndDoneAndDeductsQuota() throws Exception {
        User user = createVerifiedUser("polish");
        String requestId = UUID.randomUUID().toString();
        MvcResult result = mockMvc.perform(withToken(post("/api/ai/polish/stream"), user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("requestId", requestId, "text", "你好世界",
                                "style", "vivid", "target", "selected"))))
                .andExpect(request().asyncStarted())
                .andReturn();
        mockMvc.perform(asyncDispatch(result)).andExpect(status().isOk());
        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(body).contains("event:meta").contains("event:delta").contains("event:done");
        assertThat(quotaService.getSummary(user.getId()).freeUsedToday()).isEqualTo(1);
    }

    @Test
    void errorBeforeAnyDeltaRefundsQuota() throws Exception {
        User user = createVerifiedUser("polisherr");
        fakeLlm.setFailPolish(true);
        MvcResult result = mockMvc.perform(withToken(post("/api/ai/polish/stream"), user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("requestId", UUID.randomUUID().toString(),
                                "text", "你好世界"))))
                .andExpect(request().asyncStarted())
                .andReturn();
        mockMvc.perform(asyncDispatch(result)).andExpect(status().isOk());
        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(body).contains("event:error");
        assertThat(quotaService.getSummary(user.getId()).freeUsedToday()).isZero();
    }

    @Test
    void exhaustedQuotaRejectsPolishBeforeLlm() throws Exception {
        User user = createVerifiedUser("polishquota");
        quotaService.checkAndDeduct(user.getId(), UUID.randomUUID().toString());
        quotaService.checkAndDeduct(user.getId(), UUID.randomUUID().toString());
        quotaService.checkAndDeduct(user.getId(), UUID.randomUUID().toString());
        mockMvc.perform(withToken(post("/api/ai/polish/stream"), user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("requestId", UUID.randomUUID().toString(),
                                "text", "你好"))))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value(2011));
    }

    @Test
    void cancelFlagStopsStreamWithoutRefund() throws Exception {
        User user = createVerifiedUser("polishcancel2");
        String requestId = UUID.randomUUID().toString();
        redis.opsForValue().set("cancel:" + user.getId() + ":" + requestId, "1");
        MvcResult result = mockMvc.perform(withToken(post("/api/ai/polish/stream"), user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("requestId", requestId, "text", "你好世界"))))
                .andExpect(request().asyncStarted())
                .andReturn();
        mockMvc.perform(asyncDispatch(result)).andExpect(status().isOk());
        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(body).contains("event:cancelled").doesNotContain("event:done");
        assertThat(quotaService.getSummary(user.getId()).freeUsedToday()).isEqualTo(1);
    }

    @Test
    void cancelEndpointIsIdempotentAndDoesNotRefund() throws Exception {
        User user = createVerifiedUser("polishcancel");
        String unknown = UUID.randomUUID().toString();
        mockMvc.perform(withToken(post("/api/ai/cancel"), user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("requestId", unknown))))
                .andExpect(status().isOk());
        mockMvc.perform(withToken(post("/api/ai/cancel"), user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("requestId", unknown))))
                .andExpect(status().isOk());

        String owned = UUID.randomUUID().toString();
        quotaService.checkAndDeduct(user.getId(), owned);
        mockMvc.perform(withToken(post("/api/ai/cancel"), user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("requestId", owned))))
                .andExpect(status().isOk());
        assertThat(redis.hasKey("cancel:" + user.getId() + ":" + owned)).isTrue();
        assertThat(quotaService.getSummary(user.getId()).freeUsedToday()).isEqualTo(1);
    }

    @Test
    void validatesTextAndNoteOwnership() throws Exception {
        User owner = createVerifiedUser("polishnote");
        User other = createVerifiedUser("polishnote2");
        jdbcTemplate.update("INSERT INTO travel_note (user_id, country_code, city_code, title, content, "
                        + "content_text, visibility, takedown, deleted) "
                        + "VALUES (?, 'JP', 'TYO', '他人的游记', '<p>x</p>', 'x', 'PRIVATE', 0, 0)",
                owner.getId());
        Long noteId = jdbcTemplate.queryForObject(
                "SELECT MAX(id) FROM travel_note WHERE user_id = ?", Long.class, owner.getId());
        mockMvc.perform(withToken(post("/api/ai/polish/stream"), other)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("requestId", UUID.randomUUID().toString(),
                                "noteId", noteId, "text", "你好"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(2004));
        mockMvc.perform(withToken(post("/api/ai/polish/stream"), other)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("requestId", UUID.randomUUID().toString(),
                                "text", "a".repeat(5001)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(2001));
    }
}
