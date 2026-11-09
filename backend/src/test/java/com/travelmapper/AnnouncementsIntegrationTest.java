package com.travelmapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.travelmapper.repository.entity.User;
import com.travelmapper.support.AuthTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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

class AnnouncementsIntegrationTest extends AuthTestSupport {

    private static final DateTimeFormatter FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    @Test
    void adminLifecycleAndUserReadTracking() throws Exception {
        User user = createVerifiedUser("ann");
        String marker = unique("notice");

        MvcResult created = mockMvc.perform(asAdmin(post("/api/admin/announcements"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(announcementBody(marker + " 标题", "第一行\n第二行", null, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andReturn();
        long id = data(created).path("id").asLong();

        mockMvc.perform(withToken(get("/api/announcements/active"), user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));

        mockMvc.perform(asAdmin(put("/api/admin/announcements/" + id + "/publish")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"));

        mockMvc.perform(withToken(get("/api/announcements/active"), user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.unread").value(1))
                .andExpect(jsonPath("$.data.list[0].id").value(id))
                .andExpect(jsonPath("$.data.list[0].read").value(false))
                .andExpect(jsonPath("$.data.list[0].content").value("第一行\n第二行"));

        mockMvc.perform(withToken(post("/api/announcements/" + id + "/read"), user))
                .andExpect(status().isOk());
        mockMvc.perform(withToken(post("/api/announcements/" + id + "/read"), user))
                .andExpect(status().isOk());

        mockMvc.perform(withToken(get("/api/announcements/active"), user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unread").value(0))
                .andExpect(jsonPath("$.data.list[0].read").value(true));
        assertThat(count("announcement_read", "announcement_id = ? AND user_id = ?", id, user.getId()))
                .isEqualTo(1);

        mockMvc.perform(asAdmin(get("/api/admin/announcements")).param("keyword", marker))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].readCount").value(1))
                .andExpect(jsonPath("$.data.list[0].readRate").isNumber());

        mockMvc.perform(asAdmin(put("/api/admin/announcements/" + id + "/offline")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("OFFLINE"));
        mockMvc.perform(withToken(get("/api/announcements/active"), user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));

        mockMvc.perform(asAdmin(delete("/api/admin/announcements/" + id)))
                .andExpect(status().isOk());
        mockMvc.perform(asAdmin(get("/api/admin/announcements")).param("keyword", marker))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));
        assertThat(count("announcement_read", "announcement_id = ?", id)).isEqualTo(1);

        List<String> actions = jdbcTemplate.queryForList(
                "SELECT action FROM audit_log WHERE target_type = 'ANNOUNCEMENT' AND target_id = ? "
                        + "ORDER BY id", String.class, id);
        assertThat(actions).contains("ANNOUNCEMENT_CREATE", "ANNOUNCEMENT_PUBLISH",
                "ANNOUNCEMENT_OFFLINE", "ANNOUNCEMENT_DELETE");
    }

    @Test
    void effectiveWindowAndValidation() throws Exception {
        User user = createVerifiedUser("annwin");
        String marker = unique("window");

        MvcResult future = mockMvc.perform(asAdmin(post("/api/admin/announcements"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(announcementBody(marker + " 未来", "正文",
                                LocalDateTime.now().plusDays(1).format(FORMAT), null))))
                .andExpect(status().isOk())
                .andReturn();
        long futureId = data(future).path("id").asLong();
        mockMvc.perform(asAdmin(put("/api/admin/announcements/" + futureId + "/publish")))
                .andExpect(status().isOk());
        mockMvc.perform(withToken(get("/api/announcements/active"), user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));

        MvcResult expired = mockMvc.perform(asAdmin(post("/api/admin/announcements"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(announcementBody(marker + " 过期", "正文",
                                LocalDateTime.now().minusDays(2).format(FORMAT),
                                LocalDateTime.now().minusDays(1).format(FORMAT)))))
                .andExpect(status().isOk())
                .andReturn();
        long expiredId = data(expired).path("id").asLong();
        mockMvc.perform(asAdmin(put("/api/admin/announcements/" + expiredId + "/publish")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(2001));

        mockMvc.perform(asAdmin(post("/api/admin/announcements"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(announcementBody(marker + " 非法", "正文",
                                LocalDateTime.now().plusDays(1).format(FORMAT),
                                LocalDateTime.now().format(FORMAT)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(2001));
    }

    @Test
    void accessControl() throws Exception {
        User user = createVerifiedUser("annperm");

        mockMvc.perform(withToken(get("/api/admin/announcements"), user))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(4002));

        mockMvc.perform(post("/api/announcements/1/read"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(1006));

        mockMvc.perform(get("/api/admin/announcements"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(4002));
    }

    private Map<String, Object> announcementBody(String title, String content,
                                                 String publishAt, String expireAt) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", title);
        body.put("content", content);
        body.put("type", "NOTICE");
        body.put("pinned", false);
        if (publishAt != null) {
            body.put("publishAt", publishAt);
        }
        if (expireAt != null) {
            body.put("expireAt", expireAt);
        }
        return body;
    }

    private JsonNode data(MvcResult result) throws Exception {
        return objectMapper.readTree(
                result.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("data");
    }
}