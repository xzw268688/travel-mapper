package com.travelmapper;

import com.travelmapper.repository.entity.User;
import com.travelmapper.service.expense.ExchangeRateProvider;
import com.travelmapper.support.AuthTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(ExchangeRateRefreshIntegrationTest.StubExchangeRateProviderConfig.class)
class ExchangeRateRefreshIntegrationTest extends AuthTestSupport {

    static volatile boolean failProvider = false;

    @TestConfiguration
    static class StubExchangeRateProviderConfig {
        @Bean
        @Primary
        ExchangeRateProvider stubExchangeRateProvider() {
            return new ExchangeRateProvider() {
                @Override
                public Map<String, BigDecimal> latestToCny(Set<String> currencies) {
                    if (failProvider) {
                        throw new IllegalStateException("stub failure");
                    }
                    Map<String, BigDecimal> rates = new HashMap<>();
                    rates.put("CNY", new BigDecimal("1.000000"));
                    rates.put("TST", new BigDecimal("0.500000"));
                    rates.keySet().retainAll(currencies);
                    return rates;
                }

                @Override
                public String source() {
                    return "stub";
                }
            };
        }
    }

    @BeforeEach
    void resetProvider() {
        failProvider = false;
    }

    @AfterEach
    void cleanupTestCurrency() {
        jdbcTemplate.update("DELETE FROM exchange_rate WHERE currency = 'TST'");
    }

    @Test
    void previewDoesNotWriteAndApplyUpdatesAndAudits() throws Exception {
        mockMvc.perform(asAdmin(put("/api/admin/exchange-rates"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currency\":\"TST\",\"rateToCny\":\"0.400000\"}"))
                .andExpect(status().isOk());

        long auditBefore = count("audit_log", "action = 'RATE_REFRESH'");
        mockMvc.perform(asAdmin(get("/api/admin/exchange-rates/refresh-preview")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.source").value("stub"))
                .andExpect(jsonPath("$.data.applied").value(false))
                .andExpect(jsonPath("$.data.updatedCount").value(1))
                .andExpect(jsonPath("$.data.items[?(@.currency == 'TST')].oldRate",
                        org.hamcrest.Matchers.hasItem("0.400000")))
                .andExpect(jsonPath("$.data.items[?(@.currency == 'TST')].newRate",
                        org.hamcrest.Matchers.hasItem("0.500000")))
                .andExpect(jsonPath("$.data.items[?(@.currency == 'TST')].status",
                        org.hamcrest.Matchers.hasItem("CHANGED")))
                .andExpect(jsonPath("$.data.items[?(@.currency == 'CNY')].status",
                        org.hamcrest.Matchers.hasItem("UNCHANGED")));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT rate_to_cny FROM exchange_rate WHERE currency = 'TST'", BigDecimal.class))
                .isEqualByComparingTo("0.400000");
        assertThat(count("audit_log", "action = 'RATE_REFRESH'")).isEqualTo(auditBefore);

        mockMvc.perform(asAdmin(post("/api/admin/exchange-rates/refresh")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.applied").value(true))
                .andExpect(jsonPath("$.data.updatedCount").value(1));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT rate_to_cny FROM exchange_rate WHERE currency = 'TST'", BigDecimal.class))
                .isEqualByComparingTo("0.500000");
        assertThat(count("audit_log", "action = 'RATE_REFRESH'")).isEqualTo(auditBefore + 1);

        mockMvc.perform(asAdmin(post("/api/admin/exchange-rates/refresh")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.updatedCount").value(0));
        assertThat(count("audit_log", "action = 'RATE_REFRESH'")).isEqualTo(auditBefore + 1);
    }

    @Test
    void refreshIsAdminOnlyAndHandlesProviderFailure() throws Exception {
        User user = createVerifiedUser("rate_refresh");
        mockMvc.perform(withToken(get("/api/admin/exchange-rates/refresh-preview"), user))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(4002));
        mockMvc.perform(withToken(post("/api/admin/exchange-rates/refresh"), user))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(4002));

        failProvider = true;
        mockMvc.perform(asAdmin(get("/api/admin/exchange-rates/refresh-preview")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(2033));
    }
}