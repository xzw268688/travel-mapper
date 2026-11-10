package com.travelmapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.travelmapper.repository.entity.User;
import com.travelmapper.support.AuthTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class NoteModerationIntegrationTest extends AuthTestSupport {

    @BeforeEach
    void clearReportRateLimits() {
        Set<String> keys = redis.keys("report:*");
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
        }
    }

    @Test
    void adminCanViewPrivateTakenDownAndDeletedNotes() throws Exception {
        User author = createVerifiedUser("mod_author");
        long noteId = createNote(author, "私密游记", "<p>这是私密游记的正文内容。</p>");

        mockMvc.perform(asAdmin(get("/api/admin/notes/" + noteId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("私密游记"))
                .andExpect(jsonPath("$.data.content").value("<p>这是私密游记的正文内容。</p>"))
                .andExpect(jsonPath("$.data.authorName").value("mod_author"))
                .andExpect(jsonPath("$.data.visibility").value("PRIVATE"))
                .andExpect(jsonPath("$.data.hasActiveShare").value(false))
                .andExpect(jsonPath("$.data.takedown").value(false))
                .andExpect(jsonPath("$.data.deleted").value(false));

        mockMvc.perform(withToken(get("/api/admin/notes/" + noteId), author))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(4002));
        mockMvc.perform(asAdmin(get("/api/admin/notes/999999999")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(2004));

        mockMvc.perform(asAdmin(put("/api/admin/notes/" + noteId + "/takedown"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"含不当内容\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(withToken(get("/api/notes/" + noteId), author))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.takedown").value(true))
                .andExpect(jsonPath("$.data.takedownReason").value("含不当内容"))
                .andExpect(jsonPath("$.data.takedownAt").isString());

        long noReasonNote = createNote(author, "无原因下架", "<p>另一篇游记的正文内容。</p>");
        mockMvc.perform(asAdmin(put("/api/admin/notes/" + noReasonNote + "/takedown"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
        mockMvc.perform(withToken(get("/api/notes/" + noReasonNote), author))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.takedownReason").isEmpty());

        mockMvc.perform(withToken(delete("/api/notes/" + noteId), author))
                .andExpect(status().isOk());
        mockMvc.perform(asAdmin(get("/api/admin/notes/" + noteId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deleted").value(true))
                .andExpect(jsonPath("$.data.content").value("<p>这是私密游记的正文内容。</p>"));
    }

    @Test
    void mediaReviewQueueFiltersMarksAndResetsOnEdit() throws Exception {
        User author = createVerifiedUser("mod_media");
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String title = "含媒体待审核_" + suffix;
        long noteId = createNote(author, title, "<p>带图片的游记正文。</p>");
        String mediaKey = "note/" + author.getId() + "/2025/06/" + UUID.randomUUID() + ".jpg";
        jdbcTemplate.update("UPDATE travel_note SET images_json = ?, cover_image = ? WHERE id = ?",
                "[\"" + mediaKey + "\"]", mediaKey, noteId);

        mockMvc.perform(asAdmin(get("/api/admin/notes"))
                        .param("keyword", title)
                        .param("mediaReview", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].hasMedia").value(true));

        mockMvc.perform(asAdmin(put("/api/admin/notes/" + noteId + "/media-review")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reviewed").value(true))
                .andExpect(jsonPath("$.data.reviewedAt").isString());
        mockMvc.perform(asAdmin(get("/api/admin/notes"))
                        .param("keyword", title)
                        .param("mediaReview", "PENDING"))
                .andExpect(jsonPath("$.data.total").value(0));
        mockMvc.perform(asAdmin(get("/api/admin/notes"))
                        .param("keyword", title)
                        .param("mediaReview", "REVIEWED"))
                .andExpect(jsonPath("$.data.total").value(1));

        mockMvc.perform(asAdmin(put("/api/admin/notes/" + noteId + "/media-review")))
                .andExpect(status().isOk());
        assertThat(count("audit_log", "action = 'NOTE_MEDIA_REVIEW' AND target_id = ?", noteId))
                .isEqualTo(1);

        mockMvc.perform(withToken(put("/api/notes/" + noteId), author)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"images\":[]}"))
                .andExpect(status().isOk());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT media_reviewed_at FROM travel_note WHERE id = ?",
                java.sql.Timestamp.class, noteId)).isNull();

        String plainTitle = "无媒体游记_" + suffix;
        createNote(author, plainTitle, "<p>没有媒体的正文。</p>");
        mockMvc.perform(asAdmin(get("/api/admin/notes"))
                        .param("keyword", plainTitle)
                        .param("mediaReview", "PENDING"))
                .andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    void noteTitleIsSensitiveWordFilteredAndTruncated() throws Exception {
        User author = createVerifiedUser("mod_title");
        String original = jdbcTemplate.query(
                "SELECT config_value FROM sys_config WHERE config_key = 'sensitive.words'",
                rs -> rs.next() ? rs.getString(1) : null);
        jdbcTemplate.update("INSERT INTO sys_config (config_key, config_value, encrypted, masked, remark) "
                + "VALUES ('sensitive.words', '坏词', 0, 0, 'test') "
                + "ON DUPLICATE KEY UPDATE config_value = '坏词', encrypted = 0");
        try {
            long noteId = createNote(author, "包含坏词的标题", "<p>正文内容足够长，可以保存。</p>");
            String stored = jdbcTemplate.queryForObject(
                    "SELECT title FROM travel_note WHERE id = ?", String.class, noteId);
            assertThat(stored).contains("***").doesNotContain("坏词");

            String longTitle = "坏词".repeat(25);
            long longId = createNote(author, longTitle, "<p>超长标题过滤后的正文内容。</p>");
            String filtered = jdbcTemplate.queryForObject(
                    "SELECT title FROM travel_note WHERE id = ?", String.class, longId);
            assertThat(filtered).hasSize(50).startsWith("***");
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
    void sharePageReportsSnapshotDeduplicateAndRejectAuthorReport() throws Exception {
        User author = createVerifiedUser("mod_report_author");
        User other = createVerifiedUser("mod_reporter");
        long noteId = createNote(author, "待举报游记", "<p>这是被举报的原始正文内容。</p>");
        String token = share(author, noteId);

        MvcResult anonymous = reportRequest(token, null, Map.of(
                        "reason", "ABUSE", "description", "含有辱骂内容"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reportId").isNumber())
                .andExpect(jsonPath("$.data.duplicate").value(false))
                .andExpect(jsonPath("$.data.noteTitle").doesNotExist())
                .andReturn();
        long anonymousId = data(anonymous).path("reportId").asLong();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT reporter_user_id FROM note_report WHERE id = ?", Long.class, anonymousId)).isNull();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT note_title FROM note_report WHERE id = ?", String.class, anonymousId))
                .isEqualTo("待举报游记");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT note_content_text FROM note_report WHERE id = ?", String.class, anonymousId))
                .contains("这是被举报的原始正文内容");

        reportRequest(token, null, Map.of("reason", "ABUSE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.duplicate").value(true));
        assertThat(count("note_report", "note_id = ?", noteId)).isEqualTo(1);

        reportRequest(token, other, Map.of("reason", "PRIVACY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.duplicate").value(false));
        reportRequest(token, other, Map.of("reason", "PRIVACY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.duplicate").value(true));
        assertThat(count("note_report", "note_id = ?", noteId)).isEqualTo(2);

        reportRequest(token, author, Map.of("reason", "SPAM"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(2001));
        reportRequest(token, null, Map.of("reason", "OTHER"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(2001));
        reportRequest("s_invalid_token", null, Map.of("reason", "SPAM"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value(2007));

        mockMvc.perform(withToken(put("/api/notes/" + noteId), author)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("title", "修改后的标题",
                                "content", "<p>举报之后修改的正文内容。</p>"))))
                .andExpect(status().isOk());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT note_title FROM note_report WHERE id = ?", String.class, anonymousId))
                .isEqualTo("待举报游记");
    }

    @Test
    void anonymousReportsAreLimitedToFivePerDay() throws Exception {
        User author = createVerifiedUser("mod_limit");
        for (int i = 1; i <= 5; i++) {
            long noteId = createNote(author, "限流游记 " + i, "<p>第 " + i + " 篇举报限流测试正文。</p>");
            String token = share(author, noteId);
            reportRequest(token, null, Map.of("reason", "SPAM"))
                    .andExpect(status().isOk());
        }
        long sixth = createNote(author, "限流游记 6", "<p>第六篇举报限流测试正文。</p>");
        String sixthToken = share(author, sixth);
        reportRequest(sixthToken, null, Map.of("reason", "SPAM"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value(5004));
    }

    @Test
    void adminCanFilterHandleAndAuditReports() throws Exception {
        User author = createVerifiedUser("mod_admin_author");
        User otherAuthor = createVerifiedUser("mod_admin_other");
        User reporterA = createVerifiedUser("mod_admin_rep_a");
        User reporterB = createVerifiedUser("mod_admin_rep_b");
        String marker = "RM" + UUID.randomUUID().toString().substring(0, 8);

        long note1 = createNote(author, marker + " 游记一", "<p>" + marker + " 第一篇正文内容。</p>");
        long note2 = createNote(otherAuthor, marker + " 游记二", "<p>" + marker + " 第二篇正文内容。</p>");
        long note3 = createNote(author, marker + " 游记三", "<p>" + marker + " 第三篇正文内容。</p>");
        String token1 = share(author, note1);
        String token2 = share(otherAuthor, note2);
        String token3 = share(author, note3);

        long report1 = report(token1, reporterA, "ABUSE", marker + " 举报一");
        reportRequest(token1, null, Map.of(
                        "reason", "ABUSE", "description", marker + " 匿名举报"))
                .andExpect(status().isOk());
        long report3 = report(token2, reporterB, "SPAM", marker + " 举报二");
        long report4 = report(token3, reporterA, "PRIVACY", marker + " 举报三");

        mockMvc.perform(asAdmin(get("/api/admin/reports")).param("keyword", marker))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(4));

        mockMvc.perform(asAdmin(get("/api/admin/reports"))
                        .param("keyword", marker)
                        .param("status", "PENDING")
                        .param("reason", "SPAM"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].id").value(report3));

        long pending = count("note_report", "status = 'PENDING'");
        long accepted = count("note_report", "status = 'ACCEPTED'");
        long rejected = count("note_report", "status = 'REJECTED'");
        mockMvc.perform(asAdmin(get("/api/admin/reports/stats")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(pending + accepted + rejected))
                .andExpect(jsonPath("$.data.pendingCount").value(pending))
                .andExpect(jsonPath("$.data.acceptedCount").value(accepted))
                .andExpect(jsonPath("$.data.rejectedCount").value(rejected));

        mockMvc.perform(asAdmin(get("/api/admin/reports/" + report1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.noteTitle").value(marker + " 游记一"))
                .andExpect(jsonPath("$.data.noteContentText").value(marker + " 第一篇正文内容。"))
                .andExpect(jsonPath("$.data.authorName").value("mod_admin_author"))
                .andExpect(jsonPath("$.data.currentNoteTitle").value(marker + " 游记一"))
                .andExpect(jsonPath("$.data.authorAcceptedReportCount").value(0));

        mockMvc.perform(asAdmin(put("/api/admin/reports/" + report3 + "/reject")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT takedown FROM travel_note WHERE id = ?", Boolean.class, note2)).isFalse();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM note_report WHERE note_id = ? AND id <> ?", String.class, note1, report1))
                .isEqualTo("PENDING");

        mockMvc.perform(asAdmin(put("/api/admin/reports/" + report1 + "/accept"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("noteReason", "含不当内容"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACCEPTED"));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT takedown FROM travel_note WHERE id = ?", Boolean.class, note1)).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM note_report WHERE note_id = ? AND status = 'ACCEPTED'",
                Long.class, note1)).isEqualTo(2);
        mockMvc.perform(get("/s/" + token1))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value(2007));

        assertThat(count("audit_log", "action = 'REPORT_ACCEPT' AND target_id = ?", report1)).isEqualTo(1);
        assertThat(count("audit_log", "action = 'REPORT_REJECT' AND target_id = ?", report3)).isEqualTo(1);
        assertThat(count("audit_log", "action = 'NOTE_TAKEDOWN' AND target_id = ?", note1)).isEqualTo(1);

        mockMvc.perform(asAdmin(put("/api/admin/reports/" + report1 + "/accept"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("noteReason", "重复处理"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(2001));
        mockMvc.perform(asAdmin(get("/api/admin/reports/999999999")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(2032));
        mockMvc.perform(withToken(get("/api/admin/reports"), reporterA))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(4002));

        mockMvc.perform(withToken(delete("/api/notes/" + note3), author))
                .andExpect(status().isOk());
        mockMvc.perform(asAdmin(put("/api/admin/reports/" + report4 + "/accept"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("noteReason", "作者已删除"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.data.currentNoteDeleted").value(true));

        jdbcTemplate.update("UPDATE `user` SET status = 'DELETED' WHERE id = ?", reporterA.getId());
        jdbcTemplate.update("UPDATE `user` SET status = 'DELETED' WHERE id = ?", author.getId());
        mockMvc.perform(asAdmin(get("/api/admin/reports/" + report1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reporterName").value("匿名访客"))
                .andExpect(jsonPath("$.data.authorName").value("已注销用户"));
    }

    private ResultActions reportRequest(String token, User reporter, Map<String, Object> body)
            throws Exception {
        MockHttpServletRequestBuilder builder = post("/s/" + token + "/reports")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(body));
        if (reporter != null) {
            builder = withToken(builder, reporter);
        }
        return mockMvc.perform(builder);
    }

    private long report(String token, User reporter, String reason, String description)
            throws Exception {
        MvcResult result = reportRequest(token, reporter, Map.of(
                        "reason", reason, "description", description))
                .andExpect(status().isOk())
                .andReturn();
        return data(result).path("reportId").asLong();
    }

    private long createNote(User user, String title, String content) throws Exception {
        MvcResult result = mockMvc.perform(withToken(post("/api/notes"), user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(noteBody(title, content))))
                .andExpect(status().isOk())
                .andReturn();
        return data(result).path("id").asLong();
    }

    private String share(User user, long noteId) throws Exception {
        MvcResult result = mockMvc.perform(withToken(post("/api/notes/" + noteId + "/share"), user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expireDays\":7}"))
                .andExpect(status().isOk())
                .andReturn();
        return data(result).path("token").asText();
    }

    private Map<String, Object> noteBody(String title, String content) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", title);
        body.put("content", content);
        body.put("images", List.of());
        body.put("coverImage", null);
        body.put("countryCode", "JP");
        body.put("cityCode", "TYO");
        body.put("visibility", "PRIVATE");
        return body;
    }

    private JsonNode data(MvcResult result) throws Exception {
        return objectMapper.readTree(
                result.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("data");
    }
}
