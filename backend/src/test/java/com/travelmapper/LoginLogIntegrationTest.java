package com.travelmapper;

import com.travelmapper.repository.entity.User;
import com.travelmapper.repository.enums.UserStatus;
import com.travelmapper.support.AuthTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LoginLogIntegrationTest extends AuthTestSupport {

    private static final String USER_AGENT = "JUnit-LoginLog/1.0";

    @Test
    void successfulLoginIsRecorded() throws Exception {
        User user = createVerifiedUser("loginok");

        login(user.getUsername(), DEFAULT_PASSWORD, "203.0.113.9")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").isString());

        assertThat(latestLong("user_id", user.getUsername())).isEqualTo(user.getId());
        assertThat(latestBoolean("success", user.getUsername())).isEqualTo(true);
        assertThat(latestText("reason", user.getUsername())).isEqualTo("SUCCESS");
        assertThat(latestText("role", user.getUsername())).isEqualTo("USER");
        assertThat(latestText("ip", user.getUsername())).isEqualTo("203.0.113.9");
        assertThat(latestText("user_agent", user.getUsername())).isEqualTo(USER_AGENT);
    }

    @Test
    void failedLoginsAreClassified() throws Exception {
        User wrongPassword = createVerifiedUser("loginfail");
        login(wrongPassword.getUsername(), "WrongPassword123", "198.51.100.1")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(1001));
        assertReason(wrongPassword.getUsername(), "BAD_CREDENTIALS", wrongPassword.getId());
        assertThat(latestBoolean("success", wrongPassword.getUsername())).isEqualTo(false);

        String unknown = unique("nobody");
        login(unknown, DEFAULT_PASSWORD, "198.51.100.2")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(1001));
        assertReason(unknown, "USER_NOT_FOUND", null);

        User banned = createVerifiedUser("loginban");
        banned.setStatus(UserStatus.BANNED);
        userMapper.updateById(banned);
        login(banned.getUsername(), DEFAULT_PASSWORD, "198.51.100.3")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(1003));
        assertReason(banned.getUsername(), "ACCOUNT_BANNED", banned.getId());

        User deleted = createVerifiedUser("logindel");
        deleted.setStatus(UserStatus.DELETED);
        userMapper.updateById(deleted);
        login(deleted.getUsername(), DEFAULT_PASSWORD, "198.51.100.4")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(1007));
        assertReason(deleted.getUsername(), "ACCOUNT_DELETED", deleted.getId());

        User unverified = createVerifiedUser("loginunverified");
        unverified.setEmailVerified(false);
        userMapper.updateById(unverified);
        login(unverified.getUsername(), DEFAULT_PASSWORD, "198.51.100.5")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(1002));
        assertReason(unverified.getUsername(), "EMAIL_NOT_VERIFIED", unverified.getId());

        User locked = createVerifiedUser("loginlocked");
        String lockKey = "login:fail:" + locked.getUsername().toLowerCase(Locale.ROOT);
        redis.opsForValue().set(lockKey, "5");
        try {
            login(locked.getUsername(), DEFAULT_PASSWORD, "198.51.100.6")
                    .andExpect(status().isTooManyRequests())
                    .andExpect(jsonPath("$.code").value(5004));
            assertReason(locked.getUsername(), "RATE_LIMITED", null);
        } finally {
            redis.delete(lockKey);
        }
    }

    @Test
    void adminCanQueryAndFilterLoginLogs() throws Exception {
        User user = createVerifiedUser("logquery");
        login(user.getUsername(), DEFAULT_PASSWORD, "198.51.100.10").andExpect(status().isOk());
        login(user.getUsername(), "WrongPassword123", "198.51.100.11")
                .andExpect(status().isUnauthorized());

        mockMvc.perform(asAdmin(get("/api/admin/login-logs"))
                        .param("username", user.getUsername())
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.list[0].success").value(false))
                .andExpect(jsonPath("$.data.list[0].createdAt").exists())
                .andExpect(jsonPath("$.data.list[0].userAgent").value(USER_AGENT));

        mockMvc.perform(asAdmin(get("/api/admin/login-logs"))
                        .param("userId", String.valueOf(user.getId()))
                        .param("role", "USER")
                        .param("success", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].success").value(false))
                .andExpect(jsonPath("$.data.list[0].reason").value("BAD_CREDENTIALS"));

        mockMvc.perform(asAdmin(get("/api/admin/login-logs"))
                        .param("username", user.getUsername())
                        .param("from", "2000-01-01")
                        .param("to", "2099-12-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2));

        mockMvc.perform(asUser(get("/api/admin/login-logs")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(4002));

        mockMvc.perform(get("/api/admin/login-logs"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(4002));
    }

    private ResultActions login(String account, String password, String forwardedFor) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .header("X-Forwarded-For", forwardedFor)
                .header("User-Agent", USER_AGENT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("account", account, "password", password))));
    }

    private String latestText(String column, String username) {
        return jdbcTemplate.queryForObject(
                "SELECT " + column + " FROM login_log WHERE username = ? ORDER BY id DESC LIMIT 1",
                String.class, username);
    }

    private Long latestLong(String column, String username) {
        return jdbcTemplate.queryForObject(
                "SELECT " + column + " FROM login_log WHERE username = ? ORDER BY id DESC LIMIT 1",
                Long.class, username);
    }

    private Boolean latestBoolean(String column, String username) {
        return jdbcTemplate.queryForObject(
                "SELECT " + column + " FROM login_log WHERE username = ? ORDER BY id DESC LIMIT 1",
                Boolean.class, username);
    }

    private void assertReason(String username, String reason, Long userId) {
        assertThat(latestText("reason", username)).isEqualTo(reason);
        assertThat(latestLong("user_id", username)).isEqualTo(userId);
    }
}