package com.travelmapper.service.auth;

import com.travelmapper.repository.entity.LoginLog;
import com.travelmapper.repository.mapper.LoginLogMapper;
import com.travelmapper.service.audit.LoginLogService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LoginLogServiceTest {

    @Test
    void writeFailureIsSwallowed() {
        LoginLogMapper mapper = mock(LoginLogMapper.class);
        when(mapper.insert(any(LoginLog.class))).thenThrow(new RuntimeException("db down"));

        LoginLogService service = new LoginLogService(mapper);

        assertThatCode(() -> service.record(1L, "user", "USER", true, null, "127.0.0.1", "ua"))
                .doesNotThrowAnyException();
    }

    @Test
    void successReasonIsFilledAndFieldsAreTruncated() {
        LoginLogMapper mapper = mock(LoginLogMapper.class);
        when(mapper.insert(any(LoginLog.class))).thenReturn(1);

        LoginLogService service = new LoginLogService(mapper);
        String longAgent = "a".repeat(300);
        service.record(9L, "x".repeat(80), "USER", true, null, "1".repeat(60), longAgent);

        ArgumentCaptor<LoginLog> captor = ArgumentCaptor.forClass(LoginLog.class);
        verify(mapper).insert(captor.capture());
        LoginLog entry = captor.getValue();
        assertThat(entry.getReason()).isEqualTo(LoginLogService.SUCCESS);
        assertThat(entry.getUsername()).hasSize(64);
        assertThat(entry.getIp()).hasSize(45);
        assertThat(entry.getUserAgent()).hasSize(255);
    }
}