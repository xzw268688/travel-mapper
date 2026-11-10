package com.travelmapper;

import com.travelmapper.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("prod")
class ProdDocsIntegrationTest extends AbstractIntegrationTest {

    @DynamicPropertySource
    static void prodProperties(DynamicPropertyRegistry registry) {
        // 生产 profile 拒绝默认管理员密码；测试中显式提供与种子一致的密码以跳过该启动校验。
        registry.add("app.security.admin-password", () -> "Admin@123456");
    }

    @Test
    void publicSwaggerAndOpenApiAreDisabled() throws Exception {
        mockMvc.perform(get("/swagger-ui.html")).andExpect(status().isNotFound());
        mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isNotFound());
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isNotFound());
    }

    @Test
    void adminApiDocsRemainAvailableWithoutSwaggerLink() throws Exception {
        mockMvc.perform(asAdmin(get("/api/admin/monitor/api-docs")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paths").isMap())
                .andExpect(jsonPath("$.data['x-swagger-ui-enabled']").value(false));
    }
}