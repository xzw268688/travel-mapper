package com.travelmapper;

import com.travelmapper.common.ratelimit.RedisRateLimiter;
import com.travelmapper.support.AbstractIntegrationTest;
import com.travelmapper.support.CommonTestController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class IdempotencyRateLimitIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private RedisRateLimiter rateLimiter;

    @BeforeEach
    void reset() {
        CommonTestController.IDEMPOTENT_COUNTER.set(0);
        Set<String> idemKeys = redis.keys("idem:*");
        if (idemKeys != null && !idemKeys.isEmpty()) {
            redis.delete(idemKeys);
        }
        redis.delete("rl:test");
    }

    @Test
    void repeatedRequestReturnsFirstResultWithoutReexecution() throws Exception {
        String first = mockMvc.perform(asAdmin(post("/test/idempotency"))
                        .header("Idempotency-Key", "key-1"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String second = mockMvc.perform(asAdmin(post("/test/idempotency"))
                        .header("Idempotency-Key", "key-1"))
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotent-Replay", "true"))
                .andReturn().getResponse().getContentAsString();

        assertThat(second).isEqualTo(first);
        assertThat(CommonTestController.IDEMPOTENT_COUNTER.get()).isEqualTo(1);
    }

    @Test
    void differentOrMissingKeyExecutesAgain() throws Exception {
        mockMvc.perform(asAdmin(post("/test/idempotency"))
                        .header("Idempotency-Key", "key-a"))
                .andExpect(status().isOk());
        mockMvc.perform(asAdmin(post("/test/idempotency"))
                        .header("Idempotency-Key", "key-b"))
                .andExpect(status().isOk());
        mockMvc.perform(asAdmin(post("/test/idempotency")))
                .andExpect(status().isOk());
        assertThat(CommonTestController.IDEMPOTENT_COUNTER.get()).isEqualTo(3);
    }

    @Test
    void inFlightLockReturnsDuplicateRequest() throws Exception {
        String lockKey = "idem:1:POST:/test/idempotency:key-lock:lock";
        redis.opsForValue().set(lockKey, "1", Duration.ofSeconds(30));

        mockMvc.perform(asAdmin(post("/test/idempotency"))
                        .header("Idempotency-Key", "key-lock"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(3001));
        assertThat(CommonTestController.IDEMPOTENT_COUNTER.get()).isZero();
    }

    @Test
    void malformedIdempotencyKeyIsRejected() throws Exception {
        mockMvc.perform(asAdmin(post("/test/idempotency"))
                        .header("Idempotency-Key", "bad key with spaces"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(2001));
    }

    @Test
    void redisRateLimiterBlocksAfterLimit() {
        assertThat(rateLimiter.tryAcquire("rl:test", 2, 60)).isTrue();
        assertThat(rateLimiter.tryAcquire("rl:test", 2, 60)).isTrue();
        assertThat(rateLimiter.tryAcquire("rl:test", 2, 60)).isFalse();
        assertThatThrownBy(() -> rateLimiter.check("rl:test", 2, 60))
                .hasFieldOrPropertyWithValue("errorCode", com.travelmapper.common.api.ErrorCode.RATE_LIMITED);
    }

    @Test
    void businessAndUnexpectedErrorsUseUnifiedEnvelope() throws Exception {
        mockMvc.perform(get("/test/errors/business"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(2003))
                .andExpect(jsonPath("$.message").isString());

        mockMvc.perform(get("/test/errors/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(5001));

        mockMvc.perform(get("/test/errors/validation").param("n", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(2001));

        mockMvc.perform(get("/test/errors/duplicate"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(5002));

        mockMvc.perform(get("/definitely-not-a-route"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(4040));
    }

    @Test
    void requestIdIsPropagatedToHeaderAndResponse() throws Exception {
        mockMvc.perform(get("/test/errors/business").header("X-Request-Id", "trace-test-1"))
                .andExpect(status().isNotFound())
                .andExpect(header().string("X-Request-Id", "trace-test-1"))
                .andExpect(jsonPath("$.traceId").value("trace-test-1"));
    }
}
