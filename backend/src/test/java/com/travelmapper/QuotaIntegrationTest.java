package com.travelmapper;

import com.travelmapper.common.api.BusinessException;
import com.travelmapper.common.api.ErrorCode;
import com.travelmapper.repository.entity.User;
import com.travelmapper.repository.enums.QuotaSource;
import com.travelmapper.service.config.ConfigEntry;
import com.travelmapper.service.config.ConfigKeys;
import com.travelmapper.service.quota.QuotaSummary;
import com.travelmapper.support.AuthTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class QuotaIntegrationTest extends AuthTestSupport {

    private static final ZoneId BEIJING = ZoneId.of("Asia/Shanghai");

    @Test
    void freeQuotaIsThreePerDayAndRefundIsIdempotent() {
        User user = createVerifiedUser("free");
        String firstRequest = UUID.randomUUID().toString();
        quotaService.checkAndDeduct(user.getId(), firstRequest);
        quotaService.checkAndDeduct(user.getId(), UUID.randomUUID().toString());
        quotaService.checkAndDeduct(user.getId(), UUID.randomUUID().toString());

        assertThatThrownBy(() -> quotaService.checkAndDeduct(user.getId(), UUID.randomUUID().toString()))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.QUOTA_EXHAUSTED);

        quotaService.refund(user.getId(), firstRequest);
        assertThat(quotaService.getSummary(user.getId()).freeUsedToday()).isEqualTo(2);
        quotaService.refund(user.getId(), firstRequest);
        assertThat(quotaService.getSummary(user.getId()).freeUsedToday()).isEqualTo(2);
        assertThat(count("quota_ledger", "user_id = ? AND source = 'CONSUME'", user.getId()))
                .isGreaterThanOrEqualTo(3);
        assertThat(count("quota_ledger", "user_id = ? AND source = 'REFUND'", user.getId()))
                .isGreaterThanOrEqualTo(1);
    }

    @Test
    void exhaustedQuotaPreventsTheAiCaller() {
        User user = createVerifiedUser("aicaller");
        for (int i = 0; i < 3; i++) {
            quotaService.checkAndDeduct(user.getId(), UUID.randomUUID().toString());
        }
        AtomicBoolean aiCallerInvoked = new AtomicBoolean(false);
        assertThatThrownBy(() -> {
            quotaService.checkAndDeduct(user.getId(), UUID.randomUUID().toString());
            aiCallerInvoked.set(true);
        }).isInstanceOf(BusinessException.class);
        assertThat(aiCallerInvoked).isFalse();
    }

    @Test
    void freeQuotaIsIsolatedPerBeijingDay() {
        User user = createVerifiedUser("day");
        String yesterday = LocalDate.now(BEIJING).minusDays(1).format(DateTimeFormatter.BASIC_ISO_DATE);
        String today = LocalDate.now(BEIJING).format(DateTimeFormatter.BASIC_ISO_DATE);
        redis.opsForValue().set("quota:free:" + user.getId() + ":" + yesterday, "3");
        assertThat(quotaService.getSummary(user.getId()).freeUsedToday()).isZero();

        redis.opsForValue().set("quota:free:" + user.getId() + ":" + today, "2");
        assertThat(quotaService.getSummary(user.getId()).freeUsedToday()).isEqualTo(2);
    }

    @Test
    void packUsesEarliestExpiryAndConcurrentDeductionDoesNotOverspend() throws Exception {
        User fifo = createVerifiedUser("fifo");
        jdbcTemplate.update("INSERT INTO quota_pack (user_id, granted_count, used_count, expire_at) "
                + "VALUES (?, 1, 0, DATE_ADD(NOW(), INTERVAL 10 DAY))", fifo.getId());
        jdbcTemplate.update("INSERT INTO quota_pack (user_id, granted_count, used_count, expire_at) "
                + "VALUES (?, 1, 0, DATE_ADD(NOW(), INTERVAL 20 DAY))", fifo.getId());
        quotaService.checkAndDeduct(fifo.getId(), UUID.randomUUID().toString());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT used_count FROM quota_pack WHERE user_id = ? ORDER BY expire_at ASC LIMIT 1",
                Integer.class, fifo.getId())).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT used_count FROM quota_pack WHERE user_id = ? ORDER BY expire_at DESC LIMIT 1",
                Integer.class, fifo.getId())).isZero();

        User race = createVerifiedUser("packrace");
        jdbcTemplate.update("INSERT INTO quota_pack (user_id, granted_count, used_count, expire_at) "
                + "VALUES (?, 1, 0, DATE_ADD(NOW(), INTERVAL 10 DAY))", race.getId());
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<Boolean> first = () -> {
            start.await();
            try {
                quotaService.checkAndDeduct(race.getId(), UUID.randomUUID().toString());
                return true;
            } catch (BusinessException ex) {
                return false;
            }
        };
        Callable<Boolean> second = () -> {
            start.await();
            try {
                quotaService.checkAndDeduct(race.getId(), UUID.randomUUID().toString());
                return true;
            } catch (BusinessException ex) {
                return false;
            }
        };
        Future<Boolean> firstResult = pool.submit(first);
        Future<Boolean> secondResult = pool.submit(second);
        start.countDown();
        boolean one = firstResult.get(15, TimeUnit.SECONDS);
        boolean two = secondResult.get(15, TimeUnit.SECONDS);
        pool.shutdownNow();

        assertThat(one).isTrue();
        assertThat(two).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT used_count FROM quota_pack WHERE user_id = ?", Integer.class, race.getId()))
                .isEqualTo(1);
        assertThat(quotaService.getSummary(race.getId()).freeUsedToday()).isEqualTo(1);
    }

    @Test
    void vipDoesNotConsumeFreeOrPackAndHonoursDailyCap() {
        User user = createVerifiedUser("vip");
        quotaService.grantVip(user.getId(), 30, QuotaSource.ADMIN, null, "test");
        quotaService.checkAndDeduct(user.getId(), UUID.randomUUID().toString());
        QuotaSummary summary = quotaService.getSummary(user.getId());
        assertThat(summary.plan()).isEqualTo("VIP");
        assertThat(summary.freeUsedToday()).isZero();
        assertThat(summary.packBalance()).isZero();

        String vipKey = "quota:daily:vip:" + user.getId() + ":"
                + LocalDate.now(BEIJING).format(DateTimeFormatter.BASIC_ISO_DATE);
        redis.delete(vipKey);
        configService.updateGroup(Map.of(ConfigKeys.QUOTA_VIP_DAILY,
                ConfigEntry.plain("1", "test")), 1L);
        try {
            quotaService.checkAndDeduct(user.getId(), UUID.randomUUID().toString());
            assertThatThrownBy(() -> quotaService.checkAndDeduct(user.getId(), UUID.randomUUID().toString()))
                    .isInstanceOf(BusinessException.class)
                    .extracting(ex -> ((BusinessException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.QUOTA_EXHAUSTED);
        } finally {
            configService.updateGroup(Map.of(ConfigKeys.QUOTA_VIP_DAILY,
                    ConfigEntry.plain("200", "test")), 1L);
        }
    }

    @Test
    void packIsConsumedBeforeFreeQuotaAndExpiresFromBatch() {
        User user = createVerifiedUser("pack");
        quotaService.grantAiQuota(user.getId(), 2, null, QuotaSource.ADMIN, null, "test");
        assertThat(quotaService.getSummary(user.getId()).packBalance()).isEqualTo(2);

        quotaService.checkAndDeduct(user.getId(), UUID.randomUUID().toString());
        QuotaSummary afterOne = quotaService.getSummary(user.getId());
        assertThat(afterOne.packBalance()).isEqualTo(1);
        assertThat(afterOne.freeUsedToday()).isZero();

        quotaService.checkAndDeduct(user.getId(), UUID.randomUUID().toString());
        assertThat(quotaService.getSummary(user.getId()).packBalance()).isZero();

        quotaService.checkAndDeduct(user.getId(), UUID.randomUUID().toString());
        assertThat(quotaService.getSummary(user.getId()).freeUsedToday()).isEqualTo(1);
    }

    @Test
    void adminCanAdjustQuotaAndVipButNormalUserCannotUseAdminApi() throws Exception {
        User user = createVerifiedUser("adjust");
        mockMvc.perform(asAdmin(put("/api/admin/users/" + user.getId() + "/quota"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"AI_QUOTA\",\"delta\":100,\"reason\":\"test\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.packBalanceAfter").value(100));

        mockMvc.perform(asAdmin(put("/api/admin/users/" + user.getId() + "/quota"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"AI_QUOTA\",\"delta\":-100,\"reason\":\"test\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.packBalanceAfter").value(0));

        mockMvc.perform(asAdmin(put("/api/admin/users/" + user.getId() + "/quota"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"AI_QUOTA\",\"delta\":-1,\"reason\":\"test\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(2001));

        mockMvc.perform(asAdmin(put("/api/admin/users/" + user.getId() + "/quota"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"VIP\",\"delta\":30,\"reason\":\"test\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.vipExpireAtAfter").isString());

        mockMvc.perform(withToken(get("/api/admin/users"), user))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(4002));

        assertThat(count("quota_ledger", "user_id = ? AND source = 'ADMIN'", user.getId()))
                .isGreaterThanOrEqualTo(3);
    }

    @Test
    void adminUserListAndBanUnbanFlow() throws Exception {
        User user = createVerifiedUser("adminflow");
        mockMvc.perform(asAdmin(get("/api/admin/users")).param("keyword", user.getUsername()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].status").value("NORMAL"));

        mockMvc.perform(asAdmin(put("/api/admin/users/" + user.getId() + "/status"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"BANNED\",\"reason\":\"test\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("BANNED"));

        mockMvc.perform(withToken(get("/api/user/profile"), user))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(1003));

        mockMvc.perform(asAdmin(put("/api/admin/users/" + user.getId() + "/status"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"NORMAL\",\"reason\":\"test\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("NORMAL"));

        mockMvc.perform(asAdmin(put("/api/admin/users/" + user.getId() + "/quota"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"AI_QUOTA\",\"delta\":5,\"reason\":\"test\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.packBalanceAfter").value(5));
    }
}
