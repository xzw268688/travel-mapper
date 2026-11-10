package com.travelmapper.service.retention;

import com.travelmapper.common.time.TimeProvider;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RetentionCleanupServiceFailureTest {

    @Test
    void cleanupFailureIsSwallowed() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.update(anyString(), any(Object[].class)))
                .thenThrow(new RuntimeException("db down"));
        TimeProvider timeProvider = new TimeProvider(
                Clock.fixed(Instant.parse("2026-11-10T12:00:00Z"), TimeProvider.BEIJING));
        RetentionCleanupService service = new RetentionCleanupService(jdbcTemplate, timeProvider);

        assertThatCode(service::cleanupAuditLogs).doesNotThrowAnyException();
        assertThatCode(service::cleanupLoginLogs).doesNotThrowAnyException();
        assertThatCode(service::cleanupNoteShares).doesNotThrowAnyException();
        assertThatCode(service::expireCardKeys).doesNotThrowAnyException();
    }
}