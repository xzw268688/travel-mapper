package com.travelmapper;

import com.travelmapper.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AlertConfigIntegrationTest extends AbstractIntegrationTest {

    private static final String VALID_URL =
            "https://open.feishu.cn/open-apis/bot/v2/hook/test-token-1234";

    @Test
    void alertConfigIsMaskedPreservedAndValidated() throws Exception {
        try {
            mockMvc.perform(asAdmin(get("/api/admin/config/alert")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.enabled").isBoolean())
                    .andExpect(jsonPath("$.data.minIntervalSeconds").isNumber());

            mockMvc.perform(asAdmin(put("/api/admin/config/alert"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"enabled\":false,\"webhookUrl\":\"" + VALID_URL
                                    + "\",\"minIntervalSeconds\":120}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.enabled").value(false))
                    .andExpect(jsonPath("$.data.webhookUrl").value("****1234"))
                    .andExpect(jsonPath("$.data.minIntervalSeconds").value(120));

            mockMvc.perform(asAdmin(put("/api/admin/config/alert"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"enabled\":false,\"webhookUrl\":\"\",\"minIntervalSeconds\":180}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.webhookUrl").value("****1234"))
                    .andExpect(jsonPath("$.data.minIntervalSeconds").value(180));

            mockMvc.perform(asAdmin(put("/api/admin/config/alert"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"webhookUrl\":\"http://open.feishu.cn/open-apis/bot/v2/hook/x\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(2001));

            mockMvc.perform(asAdmin(put("/api/admin/config/alert"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"webhookUrl\":\"https://example.com/open-apis/bot/v2/hook/x\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(2001));

            mockMvc.perform(asAdmin(put("/api/admin/config/alert"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"minIntervalSeconds\":0}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(2001));

            mockMvc.perform(asAdmin(post("/api/admin/config/alert/test")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(2001));
        } finally {
            jdbcTemplate.update("DELETE FROM sys_config WHERE config_key LIKE 'alert.%'");
        }
    }
}