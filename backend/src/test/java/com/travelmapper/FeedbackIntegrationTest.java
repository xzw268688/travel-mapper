package com.travelmapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.travelmapper.repository.entity.User;
import com.travelmapper.support.AuthTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.test.web.servlet.MvcResult;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FeedbackIntegrationTest extends AuthTestSupport {

    @Test
    void submitAndTrackOwnFeedback() throws Exception {
        User alice = createVerifiedUser("fb_alice");
        User bob = createVerifiedUser("fb_bob");

        MvcResult created = mockMvc.perform(withToken(post("/api/feedback"), alice)
                        .header("User-Agent", "TestAgent/1.0")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "type", "BUG",
                                "title", "地图按钮无响应",
                                "content", "点击地图上的按钮后没有任何反应，请排查。",
                                "contact", "alice@example.com",
                                "pagePath", "/map",
                                "appVersion", "0.1.0"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").isNumber())
                .andExpect(jsonPath("$.data.status").value("NEW"))
                .andExpect(jsonPath("$.data.createdAt").isString())
                .andReturn();
        long feedbackId = objectMapper.readTree(created.getResponse().getContentAsString())
                .path("data").path("id").asLong();

        assertThat(jdbcTemplate.queryForObject(
                "SELECT user_agent FROM feedback WHERE id = ?", String.class, feedbackId))
                .isEqualTo("TestAgent/1.0");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT page_path FROM feedback WHERE id = ?", String.class, feedbackId))
                .isEqualTo("/map");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT app_version FROM feedback WHERE id = ?", String.class, feedbackId))
                .isEqualTo("0.1.0");

        mockMvc.perform(withToken(post("/api/feedback"), bob)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validBody("另一条反馈标题", "这是另一个用户提交的反馈正文内容。"))))
                .andExpect(status().isOk());

        mockMvc.perform(withToken(get("/api/feedback/mine"), alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].id").value(feedbackId))
                .andExpect(jsonPath("$.data.list[0].status").value("NEW"))
                .andExpect(jsonPath("$.data.list[0].adminNote").doesNotExist())
                .andExpect(jsonPath("$.data.list[0].handledBy").doesNotExist());

        mockMvc.perform(asAdmin(put("/api/admin/feedback/" + feedbackId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("status", "RESOLVED", "adminNote", "已修复"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RESOLVED"))
                .andExpect(jsonPath("$.data.adminNote").value("已修复"));

        mockMvc.perform(withToken(get("/api/feedback/mine"), alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list[0].status").value("RESOLVED"))
                .andExpect(jsonPath("$.data.list[0].adminNote").doesNotExist());
    }

    @Test
    void rejectsInvalidAndUnauthenticatedSubmissions() throws Exception {
        User user = createVerifiedUser("fb_invalid");

        mockMvc.perform(post("/api/feedback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validBody("未登录标题", "未登录用户提交的反馈正文内容。"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(1006));

        mockMvc.perform(withToken(post("/api/feedback"), user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "type", "NOPE",
                                "title", "非法类型",
                                "content", "这是一段长度合法的反馈正文内容。"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(2001));

        mockMvc.perform(withToken(post("/api/feedback"), user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "type", "BUG",
                                "title", "短正文",
                                "content", "太短了"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(2001));

        mockMvc.perform(withToken(post("/api/feedback"), user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "type", "BUG",
                                "title", "长".repeat(51),
                                "content", "这是一段长度合法的反馈正文内容。"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(2001));

        assertThat(count("feedback", "user_id = ?", user.getId())).isZero();
    }

    @Test
    void filtersSensitiveWordsInTitleAndContent() throws Exception {
        User user = createVerifiedUser("fb_sensitive");
        String original = jdbcTemplate.query(
                "SELECT config_value FROM sys_config WHERE config_key = 'sensitive.words'",
                rs -> rs.next() ? rs.getString(1) : null);
        jdbcTemplate.update("INSERT INTO sys_config (config_key, config_value, encrypted, masked, remark) "
                + "VALUES ('sensitive.words', '坏词', 0, 0, 'test') "
                + "ON DUPLICATE KEY UPDATE config_value = '坏词', encrypted = 0");
        try {
            mockMvc.perform(withToken(post("/api/feedback"), user)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of(
                                    "type", "OTHER",
                                    "title", "包含坏词的标题",
                                    "content", "这里也包含坏词，需要被替换成星号。"))))
                    .andExpect(status().isOk());

            Map<String, Object> row = jdbcTemplate.queryForMap(
                    "SELECT title, content FROM feedback WHERE user_id = ?", user.getId());
            assertThat((String) row.get("title")).contains("***").doesNotContain("坏词");
            assertThat((String) row.get("content")).contains("***").doesNotContain("坏词");
        } finally {
            if (original == null) {
                jdbcTemplate.update("DELETE FROM sys_config WHERE config_key = 'sensitive.words'");
            } else {
                jdbcTemplate.update("UPDATE sys_config SET config_value = ? WHERE config_key = 'sensitive.words'",
                        original);
            }
        }
    }

    @Test
    void enforcesFiveSubmissionsPerDay() throws Exception {
        User user = createVerifiedUser("fb_limit");
        for (int i = 1; i <= 5; i++) {
            mockMvc.perform(withToken(post("/api/feedback"), user)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(validBody("第 " + i + " 条反馈", "这是第 " + i + " 条反馈的正文内容。"))))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(withToken(post("/api/feedback"), user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validBody("第 6 条反馈", "这是第 6 条反馈的正文内容。"))))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value(5004));

        assertThat(count("feedback", "user_id = ?", user.getId())).isEqualTo(5);
    }

    @Test
    void adminDateFilterUsesBeijingDayBoundary() throws Exception {
        User author = createVerifiedUser("fb_tz");
        String marker = "TZ" + UUID.randomUUID().toString().substring(0, 8);
        long afterMidnight = insertFeedback(author.getId(), "BUG", "NEW", marker + " 零点后",
                marker + " 北京时间零点后的反馈正文。", LocalDateTime.of(2026, 11, 10, 0, 30));
        insertFeedback(author.getId(), "BUG", "NEW", marker + " 前一日",
                marker + " 北京时间前一日的反馈正文。", LocalDateTime.of(2026, 11, 9, 23, 30));

        mockMvc.perform(asAdmin(get("/api/admin/feedback"))
                        .param("keyword", marker)
                        .param("from", "2026-11-10")
                        .param("to", "2026-11-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].id").value(afterMidnight));
    }

    @Test
    void adminFiltersStatsDetailUpdatesAndAudits() throws Exception {
        User author = createVerifiedUser("fb_admin_a");
        User other = createVerifiedUser("fb_admin_b");
        String marker = "MARK" + UUID.randomUUID().toString().substring(0, 8);

        long newId = insertFeedback(author.getId(), "BUG", "NEW", marker + " 按钮",
                marker + " 第一个问题的正文内容。", LocalDateTime.of(2026, 11, 1, 10, 0));
        long processingId = insertFeedback(author.getId(), "FEATURE", "PROCESSING", marker + " 建议",
                marker + " 第二个建议的正文内容。", LocalDateTime.of(2026, 11, 2, 10, 0));
        long resolvedId = insertFeedback(other.getId(), "UX", "RESOLVED", "另一个反馈",
                "这是另一个用户提交的反馈正文内容。", LocalDateTime.of(2026, 11, 3, 10, 0));

        mockMvc.perform(asAdmin(get("/api/admin/feedback"))
                        .param("keyword", marker))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.list[0].id").value(processingId))
                .andExpect(jsonPath("$.data.list[1].id").value(newId));

        mockMvc.perform(asAdmin(get("/api/admin/feedback"))
                        .param("keyword", marker)
                        .param("type", "BUG"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].id").value(newId));

        mockMvc.perform(asAdmin(get("/api/admin/feedback"))
                        .param("keyword", marker)
                        .param("status", "PROCESSING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].id").value(processingId));

        mockMvc.perform(asAdmin(get("/api/admin/feedback"))
                        .param("keyword", marker)
                        .param("from", "2026-11-02")
                        .param("to", "2026-11-02"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].id").value(processingId));

        long newCount = count("feedback", "status = 'NEW'");
        long processingCount = count("feedback", "status = 'PROCESSING'");
        long resolvedCount = count("feedback", "status = 'RESOLVED'");
        mockMvc.perform(asAdmin(get("/api/admin/feedback/stats")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(newCount + processingCount + resolvedCount))
                .andExpect(jsonPath("$.data.newCount").value(newCount))
                .andExpect(jsonPath("$.data.processingCount").value(processingCount))
                .andExpect(jsonPath("$.data.resolvedCount").value(resolvedCount));

        mockMvc.perform(asAdmin(get("/api/admin/feedback/" + newId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").value(marker + " 第一个问题的正文内容。"))
                .andExpect(jsonPath("$.data.pagePath").value("/map"))
                .andExpect(jsonPath("$.data.userAgent").value("TestAgent/admin"))
                .andExpect(jsonPath("$.data.appVersion").value("0.1.0"))
                .andExpect(jsonPath("$.data.submitter").value("fb_admin_a"));

        mockMvc.perform(asAdmin(get("/api/admin/feedback/999999999")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(2031));

        mockMvc.perform(asAdmin(put("/api/admin/feedback/" + newId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("status", "PROCESSING", "adminNote", "正在排查"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PROCESSING"))
                .andExpect(jsonPath("$.data.adminNote").value("正在排查"))
                .andExpect(jsonPath("$.data.handledAt").isString());

        String auditDetail = jdbcTemplate.queryForObject(
                "SELECT detail FROM audit_log WHERE action = 'FEEDBACK_UPDATE' "
                        + "AND target_type = 'FEEDBACK' AND target_id = ? ORDER BY id DESC LIMIT 1",
                String.class, newId);
        JsonNode audit = objectMapper.readTree(auditDetail);
        assertThat(audit.path("oldStatus").asText()).isEqualTo("NEW");
        assertThat(audit.path("newStatus").asText()).isEqualTo("PROCESSING");
        assertThat(auditDetail).doesNotContain(marker);

        mockMvc.perform(asAdmin(put("/api/admin/feedback/" + newId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(2001));

        mockMvc.perform(withToken(get("/api/admin/feedback"), author))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(4002));

        jdbcTemplate.update("UPDATE `user` SET status = 'DELETED' WHERE id = ?", other.getId());
        mockMvc.perform(asAdmin(get("/api/admin/feedback/" + resolvedId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.submitter").value("已注销用户"));
    }

    private Map<String, Object> validBody(String title, String content) {
        return Map.of(
                "type", "BUG",
                "title", title,
                "content", content,
                "pagePath", "/map",
                "appVersion", "0.1.0");
    }

    private long insertFeedback(long userId, String type, String status, String title,
                                String content, LocalDateTime createdAt) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO feedback (user_id, type, title, content, page_path, user_agent, "
                            + "app_version, status, created_at, updated_at) "
                            + "VALUES (?, ?, ?, ?, '/map', 'TestAgent/admin', '0.1.0', ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, userId);
            statement.setString(2, type);
            statement.setString(3, title);
            statement.setString(4, content);
            statement.setString(5, status);
            statement.setObject(6, createdAt);
            statement.setObject(7, createdAt);
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        assertThat(key).isNotNull();
        return key.longValue();
    }
}