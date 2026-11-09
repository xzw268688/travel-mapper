package com.travelmapper.service;

import com.travelmapper.repository.enums.TripStatus;
import com.travelmapper.service.trip.TripStatusResolver;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class TripStatusResolverTest {

    private final TripStatusResolver resolver = new TripStatusResolver();
    private final LocalDate today = LocalDate.of(2026, 11, 5);

    @Test
    void manualStatusWins() {
        var result = resolver.resolve(new TripStatusResolver.Snapshot(
                true, TripStatus.COMPLETED, today.plusDays(10), today.plusDays(12),
                false, null, null), today);
        assertThat(result.effectiveStatus().name()).isEqualTo("COMPLETED");
        assertThat(result.expired()).isFalse();
    }

    @Test
    void preTripExpenseStaysPlanned() {
        var result = resolver.resolve(new TripStatusResolver.Snapshot(
                false, TripStatus.PLANNED, today.plusDays(10), today.plusDays(12),
                true, today, today), today);
        assertThat(result.effectiveStatus().name()).isEqualTo("PLANNED");
    }

    @Test
    void ongoingTripIsInProgress() {
        var result = resolver.resolve(new TripStatusResolver.Snapshot(
                false, TripStatus.PLANNED, today.minusDays(1), today.plusDays(1),
                true, today.minusDays(1), today.minusDays(1)), today);
        assertThat(result.effectiveStatus().name()).isEqualTo("IN_PROGRESS");
    }

    @Test
    void endedTripWithRecordsIsCompleted() {
        var result = resolver.resolve(new TripStatusResolver.Snapshot(
                false, TripStatus.PLANNED, today.minusDays(10), today.minusDays(5),
                true, today.minusDays(6), today.minusDays(6)), today);
        assertThat(result.effectiveStatus().name()).isEqualTo("COMPLETED");
    }

    @Test
    void endedTripWithoutRecordsIsExpiredPlan() {
        var result = resolver.resolve(new TripStatusResolver.Snapshot(
                false, TripStatus.PLANNED, today.minusDays(10), today.minusDays(5),
                false, null, null), today);
        assertThat(result.effectiveStatus().name()).isEqualTo("PLANNED");
        assertThat(result.expired()).isTrue();
    }

    @Test
    void recordOnlyTripIsCompletedOnRecordDay() {
        var result = resolver.resolve(new TripStatusResolver.Snapshot(
                false, TripStatus.PLANNED, null, null,
                true, today, today), today);
        assertThat(result.effectiveStatus().name()).isEqualTo("COMPLETED");
        assertThat(result.effectiveStartDate()).isEqualTo(today);
        assertThat(result.effectiveEndDate()).isEqualTo(today);
    }

    @Test
    void futureRecordOnlyTripStaysPlanned() {
        var result = resolver.resolve(new TripStatusResolver.Snapshot(
                false, TripStatus.PLANNED, null, null,
                true, today.plusDays(10), today.plusDays(10)), today);
        assertThat(result.effectiveStatus().name()).isEqualTo("PLANNED");
    }

    @Test
    void recordOnlyRangeIsInProgressBetweenDates() {
        var result = resolver.resolve(new TripStatusResolver.Snapshot(
                false, TripStatus.PLANNED, null, null,
                true, today.minusDays(2), today.plusDays(2)), today);
        assertThat(result.effectiveStatus().name()).isEqualTo("IN_PROGRESS");
    }
}