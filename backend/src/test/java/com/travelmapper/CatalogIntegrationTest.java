package com.travelmapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.travelmapper.common.api.BusinessException;
import com.travelmapper.common.api.ErrorCode;
import com.travelmapper.repository.entity.User;
import com.travelmapper.service.catalog.CatalogService;
import com.travelmapper.support.AuthTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CatalogIntegrationTest extends AuthTestSupport {

    @Autowired
    private CatalogService catalogService;

    @Test
    void publicListsReturnOnlyEnabledEntriesWithDisplayNames() throws Exception {
        User user = createVerifiedUser("catalog");
        jdbcTemplate.update("UPDATE country SET status = 'DISABLED' WHERE code = 'AU'");
        jdbcTemplate.update("UPDATE city SET status = 'DISABLED' WHERE country_code = 'JP' AND city_code = 'SPK'");
        try {
            mockMvc.perform(withToken(get("/api/countries"), user))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[*].code", org.hamcrest.Matchers.hasItem("JP")))
                    .andExpect(jsonPath("$.data[*].code", org.hamcrest.Matchers.not(
                            org.hamcrest.Matchers.hasItem("AU"))));

            mockMvc.perform(withToken(get("/api/cities").param("countryCode", "JP"), user))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[?(@.cityCode == 'TYO')].displayName",
                            org.hamcrest.Matchers.hasItem("东京（日本）")))
                    .andExpect(jsonPath("$.data[*].cityCode", org.hamcrest.Matchers.not(
                            org.hamcrest.Matchers.hasItem("SPK"))));

            mockMvc.perform(withToken(get("/api/cities").param("countryCode", "US"), user))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isEmpty());

            mockMvc.perform(withToken(get("/api/cities").param("countryCode", "QQ"), user))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(2002));
        } finally {
            jdbcTemplate.update("UPDATE country SET status = 'ENABLED' WHERE code = 'AU'");
            jdbcTemplate.update("UPDATE city SET status = 'ENABLED' WHERE country_code = 'JP' AND city_code = 'SPK'");
        }
    }

    @Test
    void cityValidationRejectsDisabledWrongCountryAndUnknownCities() {
        createTestCountry();
        try {
            jdbcTemplate.update("INSERT INTO city (country_code, city_code, name, level, status, sort_order) "
                    + "VALUES ('ZZ', 'TST', '测试城', 'MAJOR', 'ENABLED', 1)");
            assertThat(catalogService.requireEnabledCity("ZZ", "TST").getName()).isEqualTo("测试城");

            jdbcTemplate.update("UPDATE city SET status = 'DISABLED' WHERE country_code = 'ZZ' AND city_code = 'TST'");
            assertThatThrownBy(() -> catalogService.requireEnabledCity("ZZ", "TST"))
                    .isInstanceOf(BusinessException.class)
                    .extracting(ex -> ((BusinessException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.COUNTRY_OR_CITY_INVALID);

            assertThatThrownBy(() -> catalogService.requireEnabledCity("TH", "TYO"))
                    .isInstanceOf(BusinessException.class);
            assertThatThrownBy(() -> catalogService.requireEnabledCity("JP", "NOPE"))
                    .isInstanceOf(BusinessException.class);
            assertThat(catalogService.requireEnabledCity("JP", "TYO").getName()).isEqualTo("东京");
        } finally {
            cleanupTestCountry();
        }
    }

    @Test
    void adminCityCrudReferencedDeleteAndPermission() throws Exception {
        createTestCountry();
        User user = createVerifiedUser("cityadmin");
        try {
            MvcResult created = mockMvc.perform(asAdmin(post("/api/admin/cities"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"countryCode\":\"ZZ\",\"cityCode\":\"TST\",\"name\":\"测试城\",\"level\":\"MAJOR\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.referenced").value(false))
                    .andReturn();
            long cityId = objectMapper.readTree(created.getResponse().getContentAsString())
                    .path("data").path("id").asLong();

            mockMvc.perform(asAdmin(post("/api/admin/cities"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"countryCode\":\"ZZ\",\"cityCode\":\"TST\",\"name\":\"重复\",\"level\":\"MAJOR\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(2001));

            mockMvc.perform(asAdmin(get("/api/admin/cities"))
                            .param("countryCode", "ZZ")
                            .param("keyword", "测试城"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.total").value(1))
                    .andExpect(jsonPath("$.data.list[0].referenced").value(false));

            mockMvc.perform(asAdmin(put("/api/admin/cities/" + cityId))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"测试城市\",\"status\":\"DISABLED\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("DISABLED"));
            mockMvc.perform(withToken(get("/api/cities").param("countryCode", "ZZ"), user))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isEmpty());

            mockMvc.perform(asAdmin(put("/api/admin/cities/" + cityId))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\":\"ENABLED\"}"))
                    .andExpect(status().isOk());

            MvcResult referenced = mockMvc.perform(asAdmin(post("/api/admin/cities"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"countryCode\":\"ZZ\",\"cityCode\":\"REF\",\"name\":\"被引用城\",\"level\":\"MAJOR\"}"))
                    .andExpect(status().isOk()).andReturn();
            long referencedId = objectMapper.readTree(referenced.getResponse().getContentAsString())
                    .path("data").path("id").asLong();
            jdbcTemplate.update("INSERT INTO travel_note (user_id, country_code, city_code, title, content, "
                            + "content_text, visibility, takedown, deleted) VALUES (?, 'ZZ', 'REF', '测试', '<p>x</p>', 'x', "
                            + "'PRIVATE', 0, 0)",
                    user.getId());
            mockMvc.perform(asAdmin(delete("/api/admin/cities/" + referencedId)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value(2025));

            jdbcTemplate.update("UPDATE travel_note SET deleted = 1 WHERE user_id = ? AND city_code = 'REF'",
                    user.getId());
            mockMvc.perform(asAdmin(delete("/api/admin/cities/" + referencedId)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.deleted").value(true));

            MvcResult deletable = mockMvc.perform(asAdmin(post("/api/admin/cities"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"countryCode\":\"ZZ\",\"cityCode\":\"DEL\",\"name\":\"可删城\",\"level\":\"MAJOR\"}"))
                    .andExpect(status().isOk()).andReturn();
            long deletableId = objectMapper.readTree(deletable.getResponse().getContentAsString())
                    .path("data").path("id").asLong();
            mockMvc.perform(asAdmin(delete("/api/admin/cities/" + deletableId)))
                    .andExpect(status().isOk());

            mockMvc.perform(withToken(get("/api/admin/cities"), user))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value(4002));
        } finally {
            jdbcTemplate.update("UPDATE travel_note SET deleted = 1 WHERE city_code IN ('REF')");
            cleanupTestCountry();
        }
    }

    private void createTestCountry() {
        jdbcTemplate.update("DELETE FROM city WHERE country_code = 'ZZ'");
        jdbcTemplate.update("DELETE FROM country WHERE code = 'ZZ'");
        jdbcTemplate.update("INSERT INTO country (code, name_zh, name_en, continent, status, sort_order) "
                + "VALUES ('ZZ', '测试国', 'Testland', 'ASIA', 'ENABLED', 999)");
    }

    private void cleanupTestCountry() {
        jdbcTemplate.update("DELETE FROM city WHERE country_code = 'ZZ'");
        jdbcTemplate.update("DELETE FROM country WHERE code = 'ZZ'");
    }
}
