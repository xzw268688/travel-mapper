package com.travelmapper.common.time;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class TimeProviderTest {

    @Test
    void usesBeijingMidnightWhenJvmInstantIsPreviousUtcDay() {
        // UTC 2026-11-09 16:30 = 北京 2026-11-10 00:30
        Clock clock = Clock.fixed(Instant.parse("2026-11-09T16:30:00Z"), TimeProvider.BEIJING);
        TimeProvider time = new TimeProvider(clock);

        assertThat(time.today()).isEqualTo(LocalDate.of(2026, 11, 10));
        assertThat(time.now()).isEqualTo(LocalDateTime.of(2026, 11, 10, 0, 30));
        assertThat(time.zone()).isEqualTo(TimeProvider.BEIJING);
    }

    @Test
    void fixedClockIsIndependentOfJvmDefaultZone() {
        Clock clock = Clock.fixed(Instant.parse("2026-11-10T12:00:00Z"), TimeProvider.BEIJING);
        TimeProvider time = new TimeProvider(clock);

        assertThat(time.today()).isEqualTo(LocalDate.of(2026, 11, 10));
        assertThat(time.now()).isEqualTo(LocalDateTime.of(2026, 11, 10, 20, 0));
    }
}