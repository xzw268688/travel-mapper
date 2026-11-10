package com.travelmapper;

import com.travelmapper.repository.entity.User;
import com.travelmapper.service.auth.MailSenderHolder;
import com.travelmapper.support.AuthTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MailConfigIntegrationTest extends AuthTestSupport {

    @Autowired
    private MailSenderHolder mailSenderHolder;

    @AfterEach
    void clearMailConfig() {
        jdbcTemplate.update("DELETE FROM sys_config WHERE config_key LIKE 'mail.%'");
        mailSenderHolder.reload();
    }

    @Test
    void mailConfigMasksPasswordAndReloadsSender() throws Exception {
        mockMvc.perform(asAdmin(get("/api/admin/config/mail")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(false));

        mockMvc.perform(asAdmin(put("/api/admin/config/mail"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"enabled":true,"host":"smtp.example.com","port":587,
                                 "username":"mailer","password":"secret-pass",
                                 "from":"no-reply@example.com","smtpAuth":true,"starttls":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(true))
                .andExpect(jsonPath("$.data.host").value("smtp.example.com"))
                .andExpect(jsonPath("$.data.password").value(org.hamcrest.Matchers.startsWith("****")));
        assertThat(mailSenderHolder.isSmtpEnabled()).isTrue();

        mockMvc.perform(asAdmin(put("/api/admin/config/mail"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"enabled":false,"host":"smtp.example.com","port":587,
                                 "username":"mailer","from":"no-reply@example.com"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(false));
        assertThat(mailSenderHolder.isSmtpEnabled()).isFalse();

        mockMvc.perform(asAdmin(put("/api/admin/config/mail"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"enabled":true,"host":"","port":587,
                                 "from":"no-reply@example.com"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(2001));
    }

    @Test
    void normalUserCannotReadOrUpdateMailConfig() throws Exception {
        User user = createVerifiedUser("mailcfg");
        mockMvc.perform(withToken(get("/api/admin/config/mail"), user))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(4002));
        mockMvc.perform(withToken(put("/api/admin/config/mail"), user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(4002));
    }
}