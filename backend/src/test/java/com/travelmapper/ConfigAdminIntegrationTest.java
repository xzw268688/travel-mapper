package com.travelmapper;

import com.travelmapper.common.crypto.AesCryptoService;
import com.travelmapper.service.llm.LlmProviderRegistry;
import com.travelmapper.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ConfigAdminIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AesCryptoService crypto;

    @Autowired
    private LlmProviderRegistry llmProviderRegistry;

    @Test
    void adminCanReadConfigButNormalUserAndAnonymousCannot() throws Exception {
        mockMvc.perform(asAdmin(get("/api/admin/config/llm")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.provider").isString())
                .andExpect(jsonPath("$.data.apiKey").isString());

        mockMvc.perform(asUser(get("/api/admin/config/llm")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(4002));

        mockMvc.perform(get("/api/admin/config/llm"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(4002));
    }

    @Test
    void secretIsEncryptedMaskedAndBlankUpdatePreservesIt() throws Exception {
        mockMvc.perform(asAdmin(put("/api/admin/config/llm"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"apiKey\":\"sk-test-1234\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.apiKey").value("****1234"));

        mockMvc.perform(asAdmin(get("/api/admin/config/llm")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.apiKey").value("****1234"));

        String ciphertext = jdbcTemplate.queryForObject(
                "SELECT config_value FROM sys_config WHERE config_key = 'llm.api-key'", String.class);
        assertThat(ciphertext).isNotEqualTo("sk-test-1234");
        assertThat(crypto.decrypt(ciphertext)).isEqualTo("sk-test-1234");

        mockMvc.perform(asAdmin(put("/api/admin/config/llm"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"apiKey\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.apiKey").value("****1234"));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT config_value FROM sys_config WHERE config_key = 'llm.api-key'", String.class))
                .isEqualTo(ciphertext);
    }

    @Test
    void hotReloadSwitchesLlmProviderAndInvalidConfigKeepsOldValue() throws Exception {
        mockMvc.perform(asAdmin(put("/api/admin/config/llm"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":\"kimi\",\"model\":\"kimi-k2\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.provider").value("kimi"));

        assertThat(llmProviderRegistry.get().providerName()).isEqualTo("kimi");

        String baseUrlBefore = jdbcTemplate.queryForObject(
                "SELECT config_value FROM sys_config WHERE config_key = 'llm.base-url'", String.class);

        mockMvc.perform(asAdmin(put("/api/admin/config/llm"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"baseUrl\":\"not-a-url\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(2001));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT config_value FROM sys_config WHERE config_key = 'llm.base-url'", String.class))
                .isEqualTo(baseUrlBefore);
        assertThat(llmProviderRegistry.get().providerName()).isEqualTo("kimi");

        mockMvc.perform(asAdmin(put("/api/admin/config/llm"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"timeoutSeconds\":999}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(2001));
    }

    @Test
    void configUpdateIsAuditedWithoutSecrets() throws Exception {
        mockMvc.perform(asAdmin(put("/api/admin/config/llm"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"apiKey\":\"sk-audit-secret\"}"))
                .andExpect(status().isOk());

        String detail = jdbcTemplate.queryForObject(
                "SELECT detail FROM audit_log WHERE action = 'CONFIG_UPDATE' ORDER BY id DESC LIMIT 1",
                String.class);
        assertThat(detail).isNotNull();
        assertThat(detail).doesNotContain("sk-audit-secret");
        assertThat(detail).contains("****");
    }

    @Test
    void healthSwaggerAndRouteSurfaceAreExposed() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));

        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/api/admin/config/llm")));

        mockMvc.perform(get("/v3/api-docs/admin-config"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/api/admin/config/search")));

        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());

        mockMvc.perform(asAdmin(get("/api/legacy/1")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(4040));

        mockMvc.perform(asAdmin(get("/api/admin/config/search")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void searchAndStorageConfigEndpointsValidateAndMask() throws Exception {
        mockMvc.perform(asAdmin(get("/api/admin/config/oss")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessKey").isString())
                .andExpect(jsonPath("$.data.secretKey").isString());

        mockMvc.perform(asAdmin(put("/api/admin/config/oss"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"endpoint\":\"ftp://invalid\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(2001));

        mockMvc.perform(asAdmin(put("/api/admin/config/search"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dailyLimit\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(2001));
    }
}
