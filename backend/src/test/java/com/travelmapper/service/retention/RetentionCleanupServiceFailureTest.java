package com.travelmapper.service.retention;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RetentionCleanupServiceFailureTest {

    @Test
    void cleanupFailureIsSwallowed() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.update(anyString())).thenThrow(new RuntimeException("db down"));
        RetentionCleanupService service = new RetentionCleanupService(jdbcTemplate);

        assertThatCode(service::cleanupAuditLogs).doesNotThrowAnyException();
        assertThatCode(service::cleanupLoginLogs).doesNotThrowAnyException();
        assertThatCode(service::cleanupNoteShares).doesNotThrowAnyException();
        assertThatCode(service::expireCardKeys).doesNotThrowAnyException();
    }
}