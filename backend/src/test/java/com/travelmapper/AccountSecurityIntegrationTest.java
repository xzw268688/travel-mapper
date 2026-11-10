package com.travelmapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.travelmapper.common.config.AppProperties;
import com.travelmapper.common.security.TotpService;
import com.travelmapper.repository.entity.User;
import com.travelmapper.support.AuthTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AccountSecurityIntegrationTest extends AuthTestSupport {

    @Autowired
    private TotpService totpService;

    @Autowired
    private AppProperties properties;

    @Test
    void changePasswordRotatesSessionsAndSendsNotice() throws Exception {
        User user = createVerifiedUser("pwd");
        String oldToken = bearer(user);

        MvcResult result = mockMvc.perform(put("/api/user/password")
                        .header("Authorization", oldToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("currentPassword", DEFAULT_PASSWORD,
                                "newPassword", "NewPassword456"))))
                .andExpect(status().isOk())
                .andReturn();
        String newToken = read(result).path("data").path("token").asText();
        assertThat(newToken).isNotBlank();

        mockMvc.perform(get("/api/user/profile").header("Authorization", oldToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(1006));
        mockMvc.perform(get("/api/user/profile").header("Authorization", "Bearer " + newToken))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("account", user.getUsername(),
                                "password", "NewPassword456"))))
                .andExpect(status().isOk());
        assertThat(count("audit_log", "user_id = ? AND action = 'USER_PASSWORD_CHANGE'",
                user.getId())).isEqualTo(1);
        assertThat(capturingMail.count(user.getEmail())).isGreaterThanOrEqualTo(1);

        mockMvc.perform(put("/api/user/password")
                        .header("Authorization", "Bearer " + newToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("currentPassword", "Wrong123",
                                "newPassword", "AnotherPassword789"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(1001));
        mockMvc.perform(put("/api/user/password")
                        .header("Authorization", "Bearer " + newToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("currentPassword", "NewPassword456",
                                "newPassword", "short"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(2001));
    }

    @Test
    void changeEmailRequiresCodeAndNotifiesOldAddress() throws Exception {
        User user = createVerifiedUser("email");
        String token = bearer(user);
        String oldEmail = user.getEmail();
        String newEmail = unique("newmail") + "@example.com";
        clearEmailRateLimit(newEmail);

        mockMvc.perform(post("/api/user/email/send-code")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", newEmail))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.emailSent").value(true));
        String code = capturingMail.lastCode(newEmail);
        assertThat(code).hasSize(6);

        MvcResult result = mockMvc.perform(put("/api/user/email")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("newEmail", newEmail, "password", DEFAULT_PASSWORD,
                                "code", code))))
                .andExpect(status().isOk())
                .andReturn();
        String newToken = read(result).path("data").path("token").asText();

        mockMvc.perform(get("/api/user/profile").header("Authorization", token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(1006));
        mockMvc.perform(get("/api/user/profile").header("Authorization", "Bearer " + newToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(newEmail));
        assertThat(capturingMail.count(oldEmail)).isGreaterThanOrEqualTo(1);

        OtherUser other = createOtherUser();
        mockMvc.perform(post("/api/user/email/send-code")
                        .header("Authorization", "Bearer " + newToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", other.email()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(1008));
    }

    @Test
    void twoFactorLifecycleAndLoginChallenge() throws Exception {
        User user = createVerifiedUser("tfa");
        String token = bearer(user);
        String secret = setupTwoFactor(token);
        String firstRecoveryCode = enableTwoFactor(token, secret);

        mockMvc.perform(get("/api/user/profile").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.twoFactorEnabled").value(true));

        JsonNode login = login(user);
        assertThat(login.path("requires2fa").asBoolean()).isTrue();
        assertThat(login.path("token").isNull()).isTrue();
        String challenge = login.path("challengeToken").asText();

        mockMvc.perform(get("/api/user/profile").header("Authorization", "Bearer " + challenge))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(1006));

        mockMvc.perform(post("/api/auth/login/2fa")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("challengeToken", challenge,
                                "code", currentCode(secret)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").isString());

        String recovery = firstRecoveryCode;
        mockMvc.perform(post("/api/auth/login/2fa")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("challengeToken", login(user).path("challengeToken").asText(),
                                "code", recovery))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/auth/login/2fa")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("challengeToken", login(user).path("challengeToken").asText(),
                                "code", recovery))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(1010));

        MvcResult regenerated = mockMvc.perform(post("/api/user/2fa/recovery-codes")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("password", DEFAULT_PASSWORD,
                                "code", currentCode(secret)))))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(read(regenerated).path("data").path("recoveryCodes").size()).isEqualTo(10);

        mockMvc.perform(post("/api/user/2fa/disable")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("password", "Wrong123",
                                "code", currentCode(secret)))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(1001));
        mockMvc.perform(post("/api/user/2fa/disable")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("password", DEFAULT_PASSWORD,
                                "code", currentCode(secret)))))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/user/profile").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.twoFactorEnabled").value(false));
    }

    @Test
    void adminTwoFactorEnforcementBlocksUnboundAdmin() throws Exception {
        User admin = createVerifiedUser("admin2fa");
        jdbcTemplate.update("UPDATE user SET role = 'ADMIN' WHERE id = ?", admin.getId());
        User refreshed = userMapper.selectById(admin.getId());
        String adminToken = bearer(refreshed);
        boolean original = properties.getSecurity().isRequireAdmin2fa();
        try {
            properties.getSecurity().setRequireAdmin2fa(true);
            mockMvc.perform(get("/api/admin/users").header("Authorization", adminToken))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value(1009));

            String secret = setupTwoFactor(adminToken);
            enableTwoFactor(adminToken, secret);

            mockMvc.perform(get("/api/admin/users").header("Authorization", adminToken))
                    .andExpect(status().isOk());
        } finally {
            properties.getSecurity().setRequireAdmin2fa(original);
        }

        mockMvc.perform(get("/api/admin/users").header("Authorization", adminToken))
                .andExpect(status().isOk());
    }

    @Test
    void adminResetClearsTwoFactorAndRevokesSessions() throws Exception {
        User user = createVerifiedUser("adminreset");
        String token = bearer(user);
        String secret = setupTwoFactor(token);
        enableTwoFactor(token, secret);
        int mailBefore = capturingMail.count(user.getEmail());

        mockMvc.perform(asAdmin(post("/api/admin/users/" + user.getId() + "/2fa/reset")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/user/profile").header("Authorization", token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(1006));
        mockMvc.perform(asAdmin(get("/api/admin/users/" + user.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.twoFactorEnabled").value(false));
        assertThat(count("audit_log", "action = 'USER_2FA_RESET' AND target_id = ?",
                user.getId())).isGreaterThanOrEqualTo(1);
        assertThat(capturingMail.count(user.getEmail())).isGreaterThan(mailBefore);

        mockMvc.perform(asAdmin(post("/api/admin/users/" + user.getId() + "/2fa/reset")))
                .andExpect(status().isOk());
        mockMvc.perform(asAdmin(post("/api/admin/users/99999999/2fa/reset")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(2001));
        mockMvc.perform(post("/api/admin/users/" + user.getId() + "/2fa/reset")
                        .header("Authorization", bearer(user)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(4002));
    }

    private String setupTwoFactor(String token) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/user/2fa/setup")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.secret").isString())
                .andExpect(jsonPath("$.data.otpauthUri").isString())
                .andReturn();
        JsonNode data = read(result).path("data");
        assertThat(data.path("otpauthUri").asText()).startsWith("otpauth://totp/");
        return data.path("secret").asText();
    }

    private String enableTwoFactor(String token, String secret) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/user/2fa/enable")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("code", currentCode(secret)))))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode codes = read(result).path("data").path("recoveryCodes");
        assertThat(codes.size()).isEqualTo(10);
        return codes.get(0).asText();
    }

    private JsonNode login(User user) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("account", user.getUsername(),
                                "password", DEFAULT_PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();
        return read(result).path("data");
    }

    private String currentCode(String secret) {
        return totpService.codeAt(secret, Instant.now().getEpochSecond());
    }

    private JsonNode read(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private OtherUser createOtherUser() {
        User other = createVerifiedUser("other");
        return new OtherUser(other.getEmail());
    }

    private record OtherUser(String email) {
    }
}