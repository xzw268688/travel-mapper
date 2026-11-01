package com.travelmapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.travelmapper.common.api.BusinessException;
import com.travelmapper.repository.entity.User;
import com.travelmapper.support.AuthTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
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

class CardIntegrationTest extends AuthTestSupport {

    @Test
    void cardLifecycleIdempotencyExportAndVoid() throws Exception {
        MvcResult generated = mockMvc.perform(asAdmin(post("/api/admin/cards/generate"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"VIP\",\"value\":30,\"quantity\":2,\"prefix\":\"TM\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.quantity").value(2))
                .andReturn();
        JsonNode data = objectMapper.readTree(generated.getResponse().getContentAsString()).path("data");
        String batchId = data.path("batchId").asText();
        List<String> codes = new ArrayList<>();
        data.path("codes").forEach(node -> codes.add(node.asText()));
        assertThat(codes).hasSize(2);

        mockMvc.perform(asAdmin(get("/api/admin/cards")).param("batchId", batchId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2));

        MvcResult exported = mockMvc.perform(asAdmin(get("/api/admin/cards/export"))
                        .param("batchId", batchId))
                .andExpect(status().isOk())
                .andReturn();
        byte[] csvBytes = exported.getResponse().getContentAsByteArray();
        assertThat(csvBytes[0] & 0xFF).isEqualTo(0xEF);
        assertThat(csvBytes[1] & 0xFF).isEqualTo(0xBB);
        assertThat(csvBytes[2] & 0xFF).isEqualTo(0xBF);
        String csv = new String(csvBytes, StandardCharsets.UTF_8);
        assertThat(csv).contains("code,type,value,expireAt,status,createdAt");
        assertThat(csv).contains(codes.get(0)).contains(codes.get(1));

        User user = createVerifiedUser("carduser");
        mockMvc.perform(withToken(post("/api/card/redeem")
                        .header("Idempotency-Key", "idem-1"), user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("code", codes.get(0)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cardType").value("VIP"))
                .andExpect(jsonPath("$.data.value").value(30))
                .andExpect(jsonPath("$.data.idempotentReplay").value(false));

        mockMvc.perform(withToken(post("/api/card/redeem")
                        .header("Idempotency-Key", "idem-1"), user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("code", codes.get(0)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.idempotentReplay").value(true));

        mockMvc.perform(withToken(post("/api/card/redeem")
                        .header("Idempotency-Key", "idem-2"), user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("code", codes.get(0)))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(2009));

        Long secondCardId = jdbcTemplate.queryForObject(
                "SELECT id FROM card_key WHERE code = ?", Long.class, codes.get(1));
        mockMvc.perform(asAdmin(put("/api/admin/cards/" + secondCardId + "/void")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("VOID"));
        mockMvc.perform(withToken(post("/api/card/redeem")
                        .header("Idempotency-Key", "idem-3"), user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("code", codes.get(1)))))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value(2010));

        Long firstCardId = jdbcTemplate.queryForObject(
                "SELECT id FROM card_key WHERE code = ?", Long.class, codes.get(0));
        mockMvc.perform(asAdmin(put("/api/admin/cards/" + firstCardId + "/void")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(2001));
        mockMvc.perform(asAdmin(put("/api/admin/cards/99999999/void")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(2008));

        assertThat(count("quota_ledger", "user_id = ? AND source = 'REDEEM'", user.getId()))
                .isGreaterThanOrEqualTo(1);
        assertThat(count("audit_log", "action = 'CARD_GENERATE'")).isGreaterThanOrEqualTo(1);
        assertThat(count("audit_log", "action = 'CARD_REDEEM' AND user_id = ?", user.getId()))
                .isGreaterThanOrEqualTo(1);
        assertThat(count("audit_log", "action = 'CARD_VOID'")).isGreaterThanOrEqualTo(1);
    }

    @Test
    void fullRegisterVerifyLoginQuotaRedeemFlow() throws Exception {
        String username = unique("e2e");
        String email = username + "@example.com";
        mockMvc.perform(post("/api/auth/send-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "scene", "VERIFY"))))
                .andExpect(status().isOk());
        String verifyCode = capturingMail.lastCode(email);
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", username, "email", email,
                                "password", DEFAULT_PASSWORD, "agreeTerms", true,
                                "code", verifyCode))))
                .andExpect(status().isOk());

        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("account", username, "password", DEFAULT_PASSWORD))))
                .andExpect(status().isOk()).andReturn();
        String token = objectMapper.readTree(login.getResponse().getContentAsString())
                .path("data").path("token").asText();

        mockMvc.perform(get("/api/quota/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.plan").value("FREE"))
                .andExpect(jsonPath("$.data.packBalance").value(0));

        MvcResult generated = mockMvc.perform(asAdmin(post("/api/admin/cards/generate"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"AI_QUOTA\",\"value\":5,\"quantity\":1,\"prefix\":\"TM\"}"))
                .andExpect(status().isOk()).andReturn();
        String code = objectMapper.readTree(generated.getResponse().getContentAsString())
                .path("data").path("codes").get(0).asText();

        mockMvc.perform(post("/api/card/redeem")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", "e2e-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("code", code))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.packBalanceAfter").value(5));

        mockMvc.perform(get("/api/quota/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.packBalance").value(5));
    }

    @Test
    void concurrentRedemptionHasExactlyOneWinner() throws Exception {
        MvcResult generated = mockMvc.perform(asAdmin(post("/api/admin/cards/generate"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"AI_QUOTA\",\"value\":10,\"quantity\":1,\"prefix\":\"TM\"}"))
                .andExpect(status().isOk()).andReturn();
        String code = objectMapper.readTree(generated.getResponse().getContentAsString())
                .path("data").path("codes").get(0).asText();

        User first = createVerifiedUser("race1");
        User second = createVerifiedUser("race2");
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<Boolean> firstCall = () -> {
            start.await();
            try {
                cardKeyService.redeem(first.getId(), code, "race-first");
                return true;
            } catch (BusinessException ex) {
                return false;
            }
        };
        Callable<Boolean> secondCall = () -> {
            start.await();
            try {
                cardKeyService.redeem(second.getId(), code, "race-second");
                return true;
            } catch (BusinessException ex) {
                return false;
            }
        };
        Future<Boolean> firstResult = pool.submit(firstCall);
        Future<Boolean> secondResult = pool.submit(secondCall);
        start.countDown();
        boolean one = firstResult.get(10, TimeUnit.SECONDS);
        boolean two = secondResult.get(10, TimeUnit.SECONDS);
        pool.shutdownNow();

        assertThat(one ^ two).isTrue();
        assertThat(quotaService.packBalance(first.getId()) + quotaService.packBalance(second.getId()))
                .isEqualTo(10);
    }
}
