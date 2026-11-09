package com.travelmapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.travelmapper.common.api.BusinessException;
import com.travelmapper.controller.auth.dto.RegisterRequest;
import com.travelmapper.repository.entity.User;
import com.travelmapper.service.auth.AuthService;
import com.travelmapper.support.AuthTestSupport;
import com.travelmapper.support.TestInfrastructure;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthIntegrationTest extends AuthTestSupport {

    @Autowired
    private AuthService authService;

    @Test
    void registerVerifyLoginAndProfileFlow() throws Exception {
        String username = unique("flow");
        String email = username + "@example.com";
        // 注册前先给未注册邮箱发码
        mockMvc.perform(post("/api/auth/send-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "scene", "VERIFY"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.emailSent").value(true));
        String verifyCode = capturingMail.lastCode(email);
        assertThat(verifyCode).hasSize(6);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", username, "email", email,
                                "password", DEFAULT_PASSWORD, "nickname", "测试用户",
                                "agreeTerms", true, "code", verifyCode))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").isNumber())
                .andExpect(jsonPath("$.data.emailSent").doesNotExist());

        Long userId = jdbcTemplate.queryForObject(
                "SELECT id FROM user WHERE username = ?", Long.class, username);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT email_verified FROM user WHERE id = ?", Boolean.class, userId)).isTrue();

        // 同一验证码不可复用：换一个未注册邮箱也应失败
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", unique("flow2"), "email",
                                unique("flow2") + "@example.com", "password", DEFAULT_PASSWORD,
                                "agreeTerms", true, "code", verifyCode))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1004));

        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("account", username, "password", DEFAULT_PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.expiresIn").value(604800))
                .andExpect(jsonPath("$.data.user.plan").value("FREE"))
                .andReturn();
        JsonNode loginBody = objectMapper.readTree(login.getResponse().getContentAsString());
        String token = loginBody.path("data").path("token").asText();
        assertThat(token).isNotBlank();

        mockMvc.perform(get("/api/user/profile").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value(username))
                .andExpect(jsonPath("$.data.quota.freeLimit").value(3))
                .andExpect(jsonPath("$.data.quota.freeUsedToday").value(0))
                .andExpect(jsonPath("$.data.quota.packBalance").value(0));

        mockMvc.perform(put("/api/user/profile")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"新昵称\",\"avatarPreset\":\"preset_07\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("新昵称"))
                .andExpect(jsonPath("$.data.avatarPreset").value("preset_07"));

        mockMvc.perform(put("/api/user/profile")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"avatarPreset\":\"preset_99\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(2001));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT password_hash FROM user WHERE id = ?", String.class, userId))
                .isNotEqualTo(DEFAULT_PASSWORD)
                .startsWith("$2");
    }

    @Test
    void duplicateAndInvalidRegistrationAreRejected() throws Exception {
        String username = unique("dupreg");
        String email = username + "@example.com";
        String code = requestVerifyCode(email);
        Map<String, Object> body = Map.of("username", username, "email", email,
                "password", DEFAULT_PASSWORD, "agreeTerms", true, "code", code);
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isOk());

        // 重复注册：先查重，不消耗验证码
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(1008));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"a\",\"email\":\"valid@example.com\","
                                + "\"password\":\"Password123\",\"agreeTerms\":true,\"code\":\"123456\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(2001));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"validname\",\"email\":\"valid2@example.com\","
                                + "\"password\":\"short\",\"agreeTerms\":true,\"code\":\"123456\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(2001));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"validname\",\"email\":\"valid3@example.com\","
                                + "\"password\":\"Password123\",\"agreeTerms\":false,\"code\":\"123456\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(2001));

        // 缺少验证码直接 2001
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"validname\",\"email\":\"valid4@example.com\","
                                + "\"password\":\"Password123\",\"agreeTerms\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(2001));
    }

    @Test
    void unverifiedAccountCannotLogin() throws Exception {
        User user = createVerifiedUser("unverified");
        jdbcTemplate.update("UPDATE user SET email_verified = 0 WHERE id = ?", user.getId());
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("account", user.getUsername(), "password", DEFAULT_PASSWORD))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(1002));
    }

    @Test
    void fiveFailedLoginsLockTheAccount() throws Exception {
        User user = createVerifiedUser("lock");
        for (int i = 0; i < 4; i++) {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("account", user.getUsername(), "password", "Wrong123"))))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value(1001));
        }
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("account", user.getUsername(), "password", "Wrong123"))))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value(5004));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("account", user.getUsername(), "password", DEFAULT_PASSWORD))))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value(5004));
        loginAttemptService.clear(user.getUsername());
    }

    @Test
    void passwordResetInvalidatesOldJwtAndIsOneTime() throws Exception {
        User user = createVerifiedUser("reset");
        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("account", user.getUsername(), "password", DEFAULT_PASSWORD))))
                .andExpect(status().isOk()).andReturn();
        String oldToken = objectMapper.readTree(login.getResponse().getContentAsString())
                .path("data").path("token").asText();

        clearEmailRateLimit(user.getEmail());
        mockMvc.perform(post("/api/auth/send-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", user.getEmail(), "scene", "RESET"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.emailSent").value(true));
        String resetCode = capturingMail.lastCode(user.getEmail());
        assertThat(resetCode).hasSize(6);

        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", user.getEmail(), "code", resetCode,
                                "newPassword", "NewPassword456"))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/user/profile").header("Authorization", "Bearer " + oldToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(1006));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("account", user.getUsername(), "password", "NewPassword456"))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", user.getEmail(), "code", resetCode,
                                "newPassword", "Another789"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1004));
    }

    @Test
    void bannedUserIsBlockedAndMustLoginAgainAfterUnban() throws Exception {
        User user = createVerifiedUser("ban");
        String oldToken = bearer(user);

        mockMvc.perform(asAdmin(put("/api/admin/users/" + user.getId() + "/status"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"BANNED\",\"reason\":\"test\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("BANNED"));

        mockMvc.perform(get("/api/user/profile").header("Authorization", oldToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(1003));

        mockMvc.perform(asAdmin(put("/api/admin/users/" + user.getId() + "/status"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"NORMAL\",\"reason\":\"test\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/user/profile").header("Authorization", oldToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(1006));

        MvcResult relogin = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("account", user.getUsername(), "password", DEFAULT_PASSWORD))))
                .andExpect(status().isOk()).andReturn();
        String newToken = objectMapper.readTree(relogin.getResponse().getContentAsString())
                .path("data").path("token").asText();
        mockMvc.perform(get("/api/user/profile").header("Authorization", "Bearer " + newToken))
                .andExpect(status().isOk());
    }

    @Test
    void accountDeletionAnonymizesDataRevokesSharesAndInvalidatesJwt() throws Exception {
        User user = createVerifiedUser("delete");
        String token = bearer(user);
        long userId = user.getId();

        jdbcTemplate.update("INSERT INTO trip (user_id, country_code, name, deleted) VALUES (?, 'JP', '日本行', 0)", userId);
        jdbcTemplate.update("INSERT INTO itinerary (user_id, country_code, title, start_date, end_date, status, deleted) "
                + "VALUES (?, 'JP', '东京5日', ?, ?, 'SAVED', 0)", userId, LocalDate.now(), LocalDate.now());
        Long itineraryId = jdbcTemplate.queryForObject(
                "SELECT MAX(id) FROM itinerary WHERE user_id = ?", Long.class, userId);
        jdbcTemplate.update("INSERT INTO itinerary_day (itinerary_id, day_index, visit_date, city_code) "
                + "VALUES (?, 1, ?, 'TYO')", itineraryId, LocalDate.now());
        Long dayId = jdbcTemplate.queryForObject(
                "SELECT MAX(id) FROM itinerary_day WHERE itinerary_id = ?", Long.class, itineraryId);
        jdbcTemplate.update("INSERT INTO itinerary_spot (day_id, sort_order, source, name, description) "
                + "VALUES (?, 0, 'MANUAL', '私藏店', '很好')", dayId);
        jdbcTemplate.update("INSERT INTO travel_note (user_id, country_code, city_code, title, content, "
                + "content_text, visibility, takedown, deleted) VALUES (?, 'JP', 'TYO', '东京散步', '<p>x</p>', 'x', "
                + "'PRIVATE', 0, 0)", userId);
        Long noteId = jdbcTemplate.queryForObject(
                "SELECT MAX(id) FROM travel_note WHERE user_id = ?", Long.class, userId);
        jdbcTemplate.update("INSERT INTO note_report (note_id, reporter_user_id, reason, description, "
                + "note_title, note_content_text, note_updated_at, status) "
                + "VALUES (?, ?, 'ABUSE', 'test', '东京散步', 'x', NOW(), 'PENDING')", noteId, userId);
        String shareToken = "s_del_" + UUID.randomUUID().toString().replace("-", "");
        jdbcTemplate.update("INSERT INTO note_share (note_id, token, revoked) VALUES (?, ?, 0)", noteId, shareToken);
        jdbcTemplate.update("INSERT INTO expense (user_id, country_code, currency, amount, category, "
                + "expense_date, remark, deleted) VALUES (?, 'JP', 'JPY', 100, 'FOOD', ?, '拉面', 0)",
                userId, LocalDate.now());
        jdbcTemplate.update("INSERT INTO trip_checklist (user_id, country_code, itinerary_id, item_key, "
                + "checked, deleted) VALUES (?, 'JP', ?, 'VISA', 1, 0)", userId, itineraryId);
        jdbcTemplate.update("INSERT INTO quota_pack (user_id, granted_count, used_count, expire_at) "
                + "VALUES (?, 10, 0, DATE_ADD(NOW(), INTERVAL 30 DAY))", userId);
        jdbcTemplate.update("INSERT INTO email_token (user_id, email, token, type, expire_at, used) "
                + "VALUES (?, ?, ?, 'VERIFY', DATE_ADD(NOW(), INTERVAL 30 MINUTE), 0)",
                userId, user.getEmail(), UUID.randomUUID().toString().replace("-", ""));
        jdbcTemplate.update("INSERT INTO feedback (user_id, type, title, content, contact, status, admin_note) "
                        + "VALUES (?, 'BUG', '提交按钮无响应', '点击提交后页面没有反应，请排查。', "
                        + "'me@example.com', 'NEW', '内部备注')", userId);

        mockMvc.perform(post("/api/user/logout-account")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("password", DEFAULT_PASSWORD, "confirmText", "注销账号"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deletedAt").isString());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM user WHERE id = ?", String.class, userId)).isEqualTo("DELETED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT deleted FROM user WHERE id = ?", Integer.class, userId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT username FROM user WHERE id = ?", String.class, userId)).isEqualTo("deleted_" + userId);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT email FROM user WHERE id = ?", String.class, userId))
                .isEqualTo("deleted_" + userId + "@deleted.local");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT nickname FROM user WHERE id = ?", String.class, userId)).isEqualTo("已注销用户");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT password_hash FROM user WHERE id = ?", String.class, userId)).isEmpty();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM email_token WHERE user_id = ? AND used = 0", Integer.class, userId)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT name FROM trip WHERE user_id = ?", String.class, userId)).isEqualTo("已注销旅行");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT title FROM itinerary WHERE user_id = ?", String.class, userId)).isEqualTo("已注销行程");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT guides_json FROM itinerary WHERE user_id = ?", String.class, userId)).isNull();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT name FROM itinerary_spot WHERE day_id = ?", String.class, dayId)).isEmpty();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT title FROM travel_note WHERE user_id = ?", String.class, userId)).isEqualTo("已注销游记");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT content FROM travel_note WHERE user_id = ?", String.class, userId)).isEmpty();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT revoked FROM note_share WHERE token = ?", Integer.class, shareToken)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT remark FROM expense WHERE user_id = ?", String.class, userId)).isEmpty();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT deleted FROM trip_checklist WHERE user_id = ?", Integer.class, userId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT title FROM feedback WHERE user_id = ?", String.class, userId)).isEmpty();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT content FROM feedback WHERE user_id = ?", String.class, userId)).isEmpty();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT contact FROM feedback WHERE user_id = ?", String.class, userId)).isNull();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT admin_note FROM feedback WHERE user_id = ?", String.class, userId)).isNull();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT type FROM feedback WHERE user_id = ?", String.class, userId)).isEqualTo("BUG");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM feedback WHERE user_id = ?", String.class, userId)).isEqualTo("NEW");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT reporter_user_id FROM note_report WHERE note_id = ?", Long.class, noteId)).isNull();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT note_title FROM note_report WHERE note_id = ?", String.class, noteId)).isEmpty();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT note_content_text FROM note_report WHERE note_id = ?", String.class, noteId)).isNull();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM note_report WHERE note_id = ?", String.class, noteId)).isEqualTo("PENDING");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM quota_pack WHERE user_id = ?", Integer.class, userId)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE user_id = ? AND action = 'ACCOUNT_DELETE'",
                Integer.class, userId)).isGreaterThanOrEqualTo(1);

        mockMvc.perform(get("/api/user/profile").header("Authorization", token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(1007));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("account", "deleted_" + userId, "password", "anything"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(1007));

        mockMvc.perform(asAdmin(get("/api/admin/users")).param("keyword", "deleted_" + userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list[0].status").value("DELETED"))
                .andExpect(jsonPath("$.data.list[0].username").value("deleted_" + userId));
    }

    @Test
    void accountDeletionGuestAndAdminGuardrails() throws Exception {
        User user = createVerifiedUser("delguard");
        mockMvc.perform(post("/api/user/logout-account")
                        .header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("password", "Wrong123", "confirmText", "注销账号"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(1001));

        mockMvc.perform(post("/api/user/logout-account")
                        .header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("password", DEFAULT_PASSWORD, "confirmText", "删除"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(2001));

        mockMvc.perform(asAdmin(post("/api/user/logout-account"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("password", DEFAULT_PASSWORD, "confirmText", "注销账号"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(4002));
    }

    @Test
    void protectedEndpointsRequireJwtAndAdminEndpointsRejectNormalUsers() throws Exception {
        for (String path : new String[]{"/api/user/profile", "/api/quota/me"}) {
            mockMvc.perform(get(path))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value(1006));
        }
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(1006));

        User user = createVerifiedUser("normal");
        mockMvc.perform(withToken(get("/api/admin/users"), user))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(4002));
    }

    @Test
    void emailSendIsRateLimitedAndResendDoesNotRevealRegistration() throws Exception {
        String username = unique("mail");
        String email = username + "@example.com";
        String code = requestVerifyCode(email);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", username, "email", email,
                                "password", DEFAULT_PASSWORD, "agreeTerms", true,
                                "code", code))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/send-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "scene", "VERIFY"))))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value(1005));

        String unknown = unique("unknown") + "@example.com";
        clearEmailRateLimit(unknown);
        mockMvc.perform(post("/api/auth/send-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", unknown, "scene", "RESET"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.emailSent").value(true));
        assertThat(capturingMail.lastCode(unknown)).isNull();
    }

    @Test
    void expiredVerificationCodeIsRejected() throws Exception {
        String username = unique("expired");
        String email = username + "@example.com";
        String code = requestVerifyCode(email);
        // 模拟过期：直接删除 Redis 验证码键
        redis.delete("auth:code:verify:" + email);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", username, "email", email,
                                "password", DEFAULT_PASSWORD, "agreeTerms", true, "code", code))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1004));
        assertThat(count("user", "username = ?", username)).isZero();
    }

    @Test
    void wrongCodeFiveTimesInvalidatesVerificationCode() throws Exception {
        String username = unique("attempts");
        String email = username + "@example.com";
        String code = requestVerifyCode(email);
        String wrong = code.equals("000000") ? "111111" : "000000";
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("username", username, "email", email,
                                    "password", DEFAULT_PASSWORD, "agreeTerms", true,
                                    "code", wrong))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(1004));
        }
        // 达到 5 次后验证码作废，即使输入正确码也无法注册
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", username, "email", email,
                                "password", DEFAULT_PASSWORD, "agreeTerms", true, "code", code))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1004));
    }

    @Test
    void concurrentRegistrationHasExactlyOneWinner() throws Exception {
        String username = unique("race");
        String email = username + "@example.com";
        RegisterRequest request = new RegisterRequest();
        request.setUsername(username);
        request.setEmail(email);
        request.setPassword(DEFAULT_PASSWORD);
        request.setAgreeTerms(true);
        request.setCode(requestVerifyCode(email));

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        Future<Boolean> first = pool.submit(() -> {
            start.await();
            try {
                authService.register(request);
                return true;
            } catch (BusinessException ex) {
                return false;
            }
        });
        Future<Boolean> second = pool.submit(() -> {
            start.await();
            try {
                authService.register(request);
                return true;
            } catch (BusinessException ex) {
                return false;
            }
        });
        start.countDown();
        boolean one = first.get(10, TimeUnit.SECONDS);
        boolean two = second.get(10, TimeUnit.SECONDS);
        pool.shutdownNow();

        assertThat(one ^ two).isTrue();
        assertThat(count("user", "username = ?", username)).isEqualTo(1);
    }

    @Test
    void accountDeletionRollsBackWhenAStatementFails() throws Exception {
        User user = createVerifiedUser("rollback");
        String token = bearer(user);
        jdbcTemplate.update("INSERT INTO trip (user_id, country_code, name, deleted) "
                + "VALUES (?, 'JP', '回滚旅行', 0)", user.getId());
        jdbcTemplate.update("INSERT INTO expense (user_id, country_code, currency, amount, category, "
                + "expense_date, remark, deleted) VALUES (?, 'JP', 'JPY', 100, 'FOOD', ?, '回滚消费', 0)",
                user.getId(), LocalDate.now());

        JdbcTemplate admin = new JdbcTemplate(new DriverManagerDataSource(
                TestInfrastructure.mysqlAdminJdbcUrl(),
                TestInfrastructure.mysqlAdminUsername(),
                TestInfrastructure.mysqlAdminPassword()));
        admin.execute("DROP TRIGGER IF EXISTS tm_force_delete_fail");
        admin.execute("CREATE TRIGGER tm_force_delete_fail BEFORE UPDATE ON expense "
                + "FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'forced failure'");
        try {
            mockMvc.perform(post("/api/user/logout-account")
                            .header("Authorization", token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("password", DEFAULT_PASSWORD, "confirmText", "注销账号"))))
                    .andExpect(status().is5xxServerError());
        } finally {
            admin.execute("DROP TRIGGER IF EXISTS tm_force_delete_fail");
        }

        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM user WHERE id = ?", String.class, user.getId())).isEqualTo("NORMAL");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT deleted FROM user WHERE id = ?", Integer.class, user.getId())).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT deleted FROM trip WHERE user_id = ?", Integer.class, user.getId())).isZero();
    }

    @Test
    void quotaEndpointReturnsSummaryForCurrentUser() throws Exception {
        User user = createVerifiedUser("quotame");
        mockMvc.perform(withToken(get("/api/quota/me"), user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.plan").value("FREE"))
                .andExpect(jsonPath("$.data.freeLimit").value(3))
                .andExpect(jsonPath("$.data.freeUsedToday").value(0))
                .andExpect(jsonPath("$.data.packBalance").value(0))
                .andExpect(jsonPath("$.data.resetAt").isString());
    }

    private String requestVerifyCode(String email) throws Exception {
        mockMvc.perform(post("/api/auth/send-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "scene", "VERIFY"))))
                .andExpect(status().isOk());
        String code = capturingMail.lastCode(email);
        assertThat(code).hasSize(6);
        return code;
    }
}
