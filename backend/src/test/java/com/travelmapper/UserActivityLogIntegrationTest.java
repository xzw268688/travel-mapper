package com.travelmapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.travelmapper.repository.entity.User;
import com.travelmapper.support.AuthTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
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

class UserActivityLogIntegrationTest extends AuthTestSupport {

    @Test
    void profileItineraryAndChecklistAreLoggedWithRole() throws Exception {
        User user = createVerifiedUser("actlog");

        mockMvc.perform(withToken(put("/api/user/profile"), user)
                        .header("X-Forwarded-For", "203.0.113.20")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"新昵称\",\"avatarPreset\":\"preset_07\"}"))
                .andExpect(status().isOk());
        JsonNode profile = latestDetail("USER_PROFILE_UPDATE", user.getId());
        assertThat(profile.path("changed").toString()).contains("nickname", "avatarPreset");
        assertThat(profile.toString()).doesNotContain("新昵称");
        assertThat(latestRole("USER_PROFILE_UPDATE", user.getId())).isEqualTo("USER");
        assertThat(latestIp("USER_PROFILE_UPDATE", user.getId())).isEqualTo("203.0.113.20");

        LocalDate start = LocalDate.now().plusDays(1);
        MvcResult saved = mockMvc.perform(withToken(post("/api/itinerary/save"), user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(saveBody("审计行程", start, List.of(
                                day(1, start, "TYO", List.of(spot("浅草寺", "AI"))),
                                day(2, start.plusDays(1), "OSA",
                                        List.of(spot("大阪城", "AI"), spot("咖啡店", "MANUAL"))))))))
                .andExpect(status().isOk())
                .andReturn();
        long itineraryId = data(saved).path("itineraryId").asLong();

        JsonNode create = latestDetail("ITINERARY_CREATE", user.getId());
        assertThat(create.path("dayCount").asInt()).isEqualTo(2);
        assertThat(create.path("spotCount").asInt()).isEqualTo(3);
        assertThat(create.toString()).doesNotContain("浅草寺", "大阪城");
        assertThat(latestTargetId("ITINERARY_CREATE", user.getId())).isEqualTo(itineraryId);
        assertThat(latestRole("ITINERARY_CREATE", user.getId())).isEqualTo("USER");

        mockMvc.perform(withToken(put("/api/itinerary/" + itineraryId), user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"新标题\"}"))
                .andExpect(status().isOk());
        JsonNode update = latestDetail("ITINERARY_UPDATE", user.getId());
        assertThat(update.path("changed").toString()).contains("title");
        assertThat(update.toString()).doesNotContain("新标题");

        mockMvc.perform(withToken(put("/api/prep/checklist/batch"), user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("itineraryId", itineraryId,
                                "items", List.of(Map.of("itemKey", "VISA", "checked", true))))))
                .andExpect(status().isOk());
        JsonNode checklist = latestDetail("CHECKLIST_SAVE", user.getId());
        assertThat(checklist.path("itemCount").asInt()).isEqualTo(1);
        assertThat(checklist.path("checkedCount").asInt()).isEqualTo(1);

        mockMvc.perform(asAdmin(get("/api/admin/audit"))
                        .param("actorRole", "USER")
                        .param("action", "ITINERARY_CREATE")
                        .param("userId", String.valueOf(user.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].actorRole").value("USER"))
                .andExpect(jsonPath("$.data.list[0].targetId").value(itineraryId));

        mockMvc.perform(asUser(get("/api/admin/audit")).param("actorRole", "USER"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(4002));
    }

    @Test
    void noteExpenseAndShareOperationsAreLoggedWithoutContent() throws Exception {
        User user = createVerifiedUser("actnote");
        long noteId = createNote(user, "私密标题", "<p>正文内容</p>");

        JsonNode noteCreate = latestDetail("NOTE_CREATE", user.getId());
        assertThat(noteCreate.path("visibility").asText()).isEqualTo("PRIVATE");
        assertThat(noteCreate.path("imageCount").asInt()).isZero();
        assertThat(noteCreate.toString()).doesNotContain("私密标题", "正文内容");

        mockMvc.perform(withToken(put("/api/notes/" + noteId), user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"改后标题\"}"))
                .andExpect(status().isOk());
        JsonNode noteUpdate = latestDetail("NOTE_UPDATE", user.getId());
        assertThat(noteUpdate.path("changed").toString()).contains("title");
        assertThat(noteUpdate.toString()).doesNotContain("改后标题", "私密标题");

        mockMvc.perform(withToken(post("/api/notes/" + noteId + "/share"), user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expireDays\":7}"))
                .andExpect(status().isOk());
        assertThat(latestDetail("NOTE_SHARE", user.getId()).path("expireDays").asInt())
                .isEqualTo(7);

        mockMvc.perform(withToken(delete("/api/notes/" + noteId + "/share"), user))
                .andExpect(status().isOk());
        assertThat(latestDetail("NOTE_UNSHARE", user.getId()).path("revokedShares").asInt())
                .isEqualTo(1);

        MvcResult created = mockMvc.perform(withToken(post("/api/expenses"), user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(expenseBody("3500.00", "FOOD", "拉面"))))
                .andExpect(status().isOk())
                .andReturn();
        long expenseId = data(created).path("id").asLong();
        JsonNode expenseCreate = latestDetail("EXPENSE_CREATE", user.getId());
        assertThat(expenseCreate.path("category").asText()).isEqualTo("FOOD");
        assertThat(expenseCreate.toString()).doesNotContain("3500", "拉面");

        mockMvc.perform(withToken(put("/api/expenses/" + expenseId), user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":\"4200.00\"}"))
                .andExpect(status().isOk());
        JsonNode expenseUpdate = latestDetail("EXPENSE_UPDATE", user.getId());
        assertThat(expenseUpdate.path("changed").toString()).contains("amount");
        assertThat(expenseUpdate.toString()).doesNotContain("4200");

        mockMvc.perform(withToken(delete("/api/expenses/" + expenseId), user))
                .andExpect(status().isOk());
        assertThat(count("audit_log", "action = ? AND user_id = ?",
                "EXPENSE_DELETE", user.getId())).isEqualTo(1);
    }

    @Test
    void anonymousReportAndAdminActionCarryTheirRoles() throws Exception {
        User author = createVerifiedUser("actauthor");
        long noteId = createNote(author, "被举报游记", "<p>正文</p>");
        MvcResult share = mockMvc.perform(withToken(post("/api/notes/" + noteId + "/share"), author)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expireDays\":7}"))
                .andExpect(status().isOk())
                .andReturn();
        String token = data(share).path("token").asText();
        String anonymousIp = "198.51.100." + (1 + Math.abs(
                UUID.randomUUID().hashCode() % 200));

        mockMvc.perform(post("/s/" + token + "/reports")
                        .header("X-Forwarded-For", anonymousIp)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"SPAM\",\"description\":\"垃圾内容\"}"))
                .andExpect(status().isOk());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT actor_role FROM audit_log WHERE action = 'NOTE_REPORT' AND target_id = ? "
                        + "ORDER BY id DESC LIMIT 1", String.class, noteId))
                .isEqualTo("ANONYMOUS");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT user_id FROM audit_log WHERE action = 'NOTE_REPORT' AND target_id = ? "
                        + "ORDER BY id DESC LIMIT 1", Long.class, noteId))
                .isNull();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT ip FROM audit_log WHERE action = 'NOTE_REPORT' AND target_id = ? "
                        + "ORDER BY id DESC LIMIT 1", String.class, noteId))
                .isEqualTo(anonymousIp);

        mockMvc.perform(asAdmin(put("/api/admin/config/search"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cacheHours\":24}"))
                .andExpect(status().isOk());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT actor_role FROM audit_log WHERE action = 'CONFIG_UPDATE' AND user_id = 1 "
                        + "ORDER BY id DESC LIMIT 1", String.class))
                .isEqualTo("ADMIN");
    }

    @Test
    void failedOperationsAreNotLogged() throws Exception {
        User owner = createVerifiedUser("actown");
        User other = createVerifiedUser("actother");
        long noteId = createNote(owner, "拥有者游记", "<p>正文</p>");

        mockMvc.perform(withToken(put("/api/notes/" + noteId), other)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"试图修改\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(2004));

        assertThat(count("audit_log", "action = ? AND user_id = ?", "NOTE_UPDATE", other.getId()))
                .isZero();

        mockMvc.perform(withToken(put("/api/notes/" + noteId), owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"  \"}"))
                .andExpect(status().isBadRequest());
        assertThat(count("audit_log", "action = ? AND user_id = ?", "NOTE_UPDATE", owner.getId()))
                .isZero();
    }

    private long createNote(User user, String title, String content) throws Exception {
        MvcResult result = mockMvc.perform(withToken(post("/api/notes"), user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(noteBody(title, content))))
                .andExpect(status().isOk())
                .andReturn();
        return data(result).path("id").asLong();
    }

    private JsonNode latestDetail(String action, long userId) throws Exception {
        String detail = jdbcTemplate.queryForObject(
                "SELECT detail FROM audit_log WHERE action = ? AND user_id = ? "
                        + "ORDER BY id DESC LIMIT 1", String.class, action, userId);
        return objectMapper.readTree(detail == null ? "{}" : detail);
    }

    private String latestRole(String action, long userId) {
        return jdbcTemplate.queryForObject(
                "SELECT actor_role FROM audit_log WHERE action = ? AND user_id = ? "
                        + "ORDER BY id DESC LIMIT 1", String.class, action, userId);
    }

    private String latestIp(String action, long userId) {
        return jdbcTemplate.queryForObject(
                "SELECT ip FROM audit_log WHERE action = ? AND user_id = ? "
                        + "ORDER BY id DESC LIMIT 1", String.class, action, userId);
    }

    private Long latestTargetId(String action, long userId) {
        return jdbcTemplate.queryForObject(
                "SELECT target_id FROM audit_log WHERE action = ? AND user_id = ? "
                        + "ORDER BY id DESC LIMIT 1", Long.class, action, userId);
    }

    private JsonNode data(MvcResult result) throws Exception {
        return objectMapper.readTree(
                result.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("data");
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

    private Map<String, Object> expenseBody(String amount, String category, String remark) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("countryCode", "JP");
        body.put("cityCode", "TYO");
        body.put("currency", "JPY");
        body.put("amount", amount);
        body.put("category", category);
        body.put("expenseDate", LocalDate.now().toString());
        body.put("remark", remark);
        return body;
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
}