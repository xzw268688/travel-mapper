package com.travelmapper.common.security;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class JwtDenylistServiceTest {

    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> ops = mock(ValueOperations.class);

    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);

    private JwtDenylistService service() {
        when(redis.opsForValue()).thenReturn(ops);
        return new JwtDenylistService(redis);
    }

    @Test
    void revokeWritesKeyWithRemainingTtl() {
        service().revoke("jti-1", 120);
        verify(ops).set("auth:jti:jti-1", "1", Duration.ofSeconds(120));
    }

    @Test
    void revokedTokenIsDetected() {
        when(redis.hasKey("auth:jti:jti-1")).thenReturn(true);
        assertThat(service().isRevoked("jti-1")).isTrue();
    }

    @Test
    void blankJtiIsIgnored() {
        JwtDenylistService service = service();
        service.revoke(null, 100);
        service.revoke(" ", 100);
        assertThat(service.isRevoked(null)).isFalse();
        assertThat(service.isRevoked(" ")).isFalse();
        verifyNoInteractions(ops);
    }

    @Test
    void writeFailureIsSwallowed() {
        doThrow(new RuntimeException("redis down"))
                .when(ops).set(anyString(), anyString(), any(Duration.class));
        assertThatCode(() -> service().revoke("jti-1", 60)).doesNotThrowAnyException();
    }

    @Test
    void readFailureDegradesToAllow() {
        when(redis.hasKey(anyString())).thenThrow(new RuntimeException("redis down"));
        assertThat(service().isRevoked("jti-1")).isFalse();
    }
}