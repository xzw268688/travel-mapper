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
import java.util.Random;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ExpenseRateIntegrationTest extends AuthTestSupport {

    @Test
    void expenseCrudSnapshotAndValidation() throws Exception {
        User user = createVerifiedUser("expense");
        MvcResult created = mockMvc.perform(withToken(post("/api/expenses"), user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(expenseBody("JP", "TYO", null, null, "JPY", "3500.00",
                                "FOOD", LocalDate.now().toString(), "拉面"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rateMissing").value(false))
                .andExpect(jsonPath("$.data.exchangeRateSnapshot").value("0.048000"))
                .andExpect(jsonPath("$.data.amountCny").value("168.00"))
                .andReturn();
        long expenseId = data(created).path("id").asLong();

        mockMvc.perform(withToken(get("/api/expenses"), user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].countryName").value("日本"))
                .andExpect(jsonPath("$.data.list[0].cityName").value("东京"))
                .andExpect(jsonPath("$.data.list[0].amountCny").value("168.00"));

        mockMvc.perform(withToken(put("/api/expenses/" + expenseId), user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":\"7000.00\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.exchangeRateSnapshot").value("0.048000"))
                .andExpect(jsonPath("$.data.amountCny").value("336.00"));
        mockMvc.perform(withToken(put("/api/expenses/" + expenseId), user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"remark\":\"只改备注\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.exchangeRateSnapshot").value("0.048000"));

        User other = createVerifiedUser("expense2");
        mockMvc.perform(withToken(put("/api/expenses/" + expenseId), other)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"remark\":\"越权\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(2005));

        mockMvc.perform(withToken(post("/api/expenses"), user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(expenseBody("JP", "TYO", null, null, "XYZ", "100.00",
                                "FOOD", LocalDate.now().toString(), null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rateMissing").value(true))
                .andExpect(jsonPath("$.data.amountCny").value(org.hamcrest.Matchers.nullValue()));
        mockMvc.perform(withToken(post("/api/expenses"), user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(expenseBody("JP", null, null, null, "CNY", "-100.00",
                                "FOOD", LocalDate.now().toString(), "退款"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.amountCny").value("-100.00"));

        mockMvc.perform(withToken(get("/api/expenses/summary"), user)
                        .param("groupBy", "country"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hasUnconfiguredCurrency").value(true))
                .andExpect(jsonPath("$.data.unconfiguredCurrencies[0]").value("XYZ"));

        mockMvc.perform(withToken(post("/api/expenses"), user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(expenseBody(null, "TYO", null, null, "CNY", "1.00",
                                "FOOD", LocalDate.now().toString(), null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(2001));
        mockMvc.perform(withToken(post("/api/expenses"), user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(expenseBody("JP", "NOPE", null, null, "CNY", "1.00",
                                "FOOD", LocalDate.now().toString(), null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(2002));
        mockMvc.perform(withToken(post("/api/expenses"), user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(expenseBody("JP", "TYO", null, null, "CNY", "1.234",
                                "FOOD", LocalDate.now().toString(), null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(2001));
        mockMvc.perform(withToken(post("/api/expenses"), user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(expenseBody("JP", "TYO", null, null, "CNY", "1.00",
                                "NOPE", LocalDate.now().toString(), null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(2001));
        mockMvc.perform(withToken(post("/api/expenses"), user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(expenseBody("JP", "TYO", null, null, "CNY", "1.00",
                                "FOOD", LocalDate.now().plusDays(1).toString(), null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(2001));

        mockMvc.perform(withToken(delete("/api/expenses/" + expenseId), user))
                .andExpect(status().isOk());
        mockMvc.perform(withToken(get("/api/expenses"), user))
                .andExpect(jsonPath("$.data.total").value(2));
    }

    @Test
    void byDateGroupsAllMatchingExpensesWithOriginalCurrencyTotals() throws Exception {
        User user = createVerifiedUser("expenseday");
        String today = LocalDate.now().toString();
        String yesterday = LocalDate.now().minusDays(1).toString();
        for (Map<String, Object> body : List.of(
                expenseBody("JP", "TYO", null, null, "JPY", "1000.00", "FOOD", today, "午餐"),
                expenseBody("JP", null, null, null, "CNY", "50.00", "FOOD", today, "小吃"),
                expenseBody("JP", null, null, null, "XYZ", "10.00", "FOOD", today, "未知币种"),
                expenseBody("JP", null, null, null, "CNY", "20.00", "SHOPPING", yesterday, "购物"))) {
            mockMvc.perform(withToken(post("/api/expenses"), user)
                            .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(withToken(get("/api/expenses/by-date"), user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCny").value("118.00"))
                .andExpect(jsonPath("$.data.hasUnconfiguredCurrency").value(true))
                .andExpect(jsonPath("$.data.unconfiguredCurrencies[0]").value("XYZ"))
                .andExpect(jsonPath("$.data.groups.length()").value(2))
                .andExpect(jsonPath("$.data.groups[0].date").value(today))
                .andExpect(jsonPath("$.data.groups[0].totalCny").value("98.00"))
                .andExpect(jsonPath("$.data.groups[0].items.length()").value(3))
                .andExpect(jsonPath("$.data.groups[0].originalCurrencies[?(@.currency == 'JPY')].total")
                        .value(org.hamcrest.Matchers.hasItem("1000.00")))
                .andExpect(jsonPath("$.data.groups[1].date").value(yesterday))
                .andExpect(jsonPath("$.data.groups[1].totalCny").value("20.00"));

        mockMvc.perform(withToken(get("/api/expenses/by-date"), user)
                        .param("category", "FOOD")
                        .param("from", today)
                        .param("to", today))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCny").value("98.00"))
                .andExpect(jsonPath("$.data.groups.length()").value(1));

        User other = createVerifiedUser("expenseday2");
        mockMvc.perform(withToken(get("/api/expenses/by-date"), other))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.groups.length()").value(0));
    }

    @Test
    void summaryGroupingAndTripAssociation() throws Exception {
        User user = createVerifiedUser("expsum");
        long tripId = insertTrip(user, "JP", "日本旅行");
        long itineraryId = insertItinerary(user, tripId);

        createExpense(user, "JP", "TYO", tripId, "JPY", "1000.00", "FOOD");
        createExpense(user, "JP", "OSA", tripId, "USD", "10.00", "TRANSPORT");
        createExpense(user, "JP", "TYO", tripId, "CNY", "20.00", "FOOD");
        // 日期在旅行区间之外：自动新建一个单日归档
        MvcResult separate = mockMvc.perform(withToken(post("/api/expenses"), user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(expenseBody("JP", "TYO", null, null, "CNY", "5.00", "FOOD",
                                LocalDate.now().minusDays(60).toString(), null))))
                .andExpect(status().isOk())
                .andReturn();
        long separateTripId = jdbcTemplate.queryForObject(
                "SELECT trip_id FROM expense WHERE id = ?", Long.class,
                data(separate).path("id").asLong());

        mockMvc.perform(withToken(get("/api/expenses/summary"), user)
                        .param("groupBy", "country"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCny").value("145.00"))
                .andExpect(jsonPath("$.data.groups.length()").value(1))
                .andExpect(jsonPath("$.data.groups[0].label").value("日本"))
                .andExpect(jsonPath("$.data.groups[0].originalCurrencies.length()").value(3));

        mockMvc.perform(withToken(get("/api/expenses/summary"), user)
                        .param("groupBy", "trip"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.groups.length()").value(2))
                .andExpect(jsonPath("$.data.groups[?(@.key == '" + separateTripId + "')].totalCny")
                        .value(org.hamcrest.Matchers.hasItem("5.00")))
                .andExpect(jsonPath("$.data.groups[?(@.key == '" + tripId + "')].totalCny")
                        .value(org.hamcrest.Matchers.hasItem("140.00")));

        mockMvc.perform(withToken(get("/api/expenses/summary"), user)
                        .param("groupBy", "city,trip"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.groups[?(@.key == 'TYO|" + tripId + "')].totalCny")
                        .value(org.hamcrest.Matchers.hasItem("68.00")))
                .andExpect(jsonPath("$.data.groups[?(@.key == 'OSA|" + tripId + "')].totalCny")
                        .value(org.hamcrest.Matchers.hasItem("72.00")));

        MvcResult linked = mockMvc.perform(withToken(post("/api/expenses"), user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(expenseBody("JP", null, itineraryId, null, "CNY", "1.00",
                                "FOOD", LocalDate.now().toString(), null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tripInherited").value(true))
                .andExpect(jsonPath("$.data.tripId").value(tripId))
                .andReturn();
        assertThat(data(linked).path("id").asLong()).isPositive();
    }

    @Test
    void exchangeRateBackfillAndUpdateSemantics() throws Exception {
        User user = createVerifiedUser("rates");
        String currency = "Q" + (char) ('A' + new Random().nextInt(26))
                + (char) ('A' + new Random().nextInt(26));
        jdbcTemplate.update("DELETE FROM exchange_rate WHERE currency = ?", currency);
        mockMvc.perform(withToken(post("/api/expenses"), user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(expenseBody("JP", "TYO", null, null, currency, "100.00",
                                "FOOD", LocalDate.now().toString(), null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rateMissing").value(true));

        mockMvc.perform(asAdmin(put("/api/admin/exchange-rates"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("currency", currency, "rateToCny", "0.010000"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.created").value(true))
                .andExpect(jsonPath("$.data.affectedUnconfiguredRecords")
                        .value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
        mockMvc.perform(withToken(get("/api/expenses"), user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list[0].rateMissing").value(false))
                .andExpect(jsonPath("$.data.list[0].exchangeRateSnapshot").value("0.010000"))
                .andExpect(jsonPath("$.data.list[0].amountCny").value("1.00"));

        mockMvc.perform(asAdmin(put("/api/admin/exchange-rates"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("currency", currency, "rateToCny", "0.020000"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.created").value(false))
                .andExpect(jsonPath("$.data.previousRate").value("0.010000"))
                .andExpect(jsonPath("$.data.affectedUnconfiguredRecords").value(0));
        mockMvc.perform(withToken(get("/api/expenses"), user))
                .andExpect(jsonPath("$.data.list[0].exchangeRateSnapshot").value("0.010000"))
                .andExpect(jsonPath("$.data.list[0].amountCny").value("1.00"));

        mockMvc.perform(withToken(post("/api/expenses"), user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(expenseBody("JP", "TYO", null, null, currency, "100.00",
                                "FOOD", LocalDate.now().toString(), null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.exchangeRateSnapshot").value("0.020000"))
                .andExpect(jsonPath("$.data.amountCny").value("2.00"));

        mockMvc.perform(withToken(get("/api/admin/exchange-rates"), user))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(4002));
        mockMvc.perform(asAdmin(put("/api/admin/exchange-rates"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currency\":\"TOOLONG\",\"rateToCny\":\"1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(2001));
    }

    private void createExpense(User user, String country, String city, Long tripId,
                               String currency, String amount, String category) throws Exception {
        mockMvc.perform(withToken(post("/api/expenses"), user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(expenseBody(country, city, null, tripId, currency, amount,
                                category, LocalDate.now().toString(), null))))
                .andExpect(status().isOk());
    }

    private Map<String, Object> expenseBody(String country, String city, Long itineraryId,
                                            Long tripId, String currency, String amount,
                                            String category, String date, String remark) {
        Map<String, Object> body = new LinkedHashMap<>();
        if (country != null) body.put("countryCode", country);
        if (city != null) body.put("cityCode", city);
        if (itineraryId != null) body.put("itineraryId", itineraryId);
        if (tripId != null) body.put("tripId", tripId);
        body.put("currency", currency);
        body.put("amount", amount);
        body.put("category", category);
        body.put("expenseDate", date);
        if (remark != null) body.put("remark", remark);
        return body;
    }

    private long insertTrip(User user, String country, String name) {
        jdbcTemplate.update("INSERT INTO trip (user_id, country_code, name, start_date, end_date, deleted) "
                + "VALUES (?, ?, ?, ?, ?, 0)", user.getId(), country, name,
                LocalDate.now(), LocalDate.now().plusDays(2));
        return jdbcTemplate.queryForObject(
                "SELECT MAX(id) FROM trip WHERE user_id = ?", Long.class, user.getId());
    }

    private long insertItinerary(User user, long tripId) {
        jdbcTemplate.update("INSERT INTO itinerary (user_id, trip_id, country_code, title, start_date, "
                        + "end_date, status, deleted) VALUES (?, ?, 'JP', ?, ?, ?, 'SAVED', 0)",
                user.getId(), tripId, "行程" + UUID.randomUUID(), LocalDate.now(), LocalDate.now().plusDays(1));
        long itineraryId = jdbcTemplate.queryForObject(
                "SELECT MAX(id) FROM itinerary WHERE user_id = ?", Long.class, user.getId());
        jdbcTemplate.update("INSERT INTO itinerary_day (itinerary_id, day_index, visit_date, city_code) "
                + "VALUES (?, 1, ?, 'TYO')", itineraryId, LocalDate.now());
        return itineraryId;
    }

    private JsonNode data(MvcResult result) throws Exception {
        return objectMapper.readTree(
                result.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("data");
    }
}
