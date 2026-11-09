package com.travelmapper.service.trip;

import com.travelmapper.repository.enums.EffectiveTripStatus;
import com.travelmapper.repository.enums.TripStatus;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 计算旅行的有效状态。手动状态优先；否则按旅行日期与游记/消费记录推导。
 */
@Component
public class TripStatusResolver {

    public record Snapshot(
            boolean statusManual,
            TripStatus storedStatus,
            LocalDate startDate,
            LocalDate endDate,
            boolean hasRecords,
            LocalDate recordStart,
            LocalDate recordEnd) {
    }

    public record Result(
            EffectiveTripStatus effectiveStatus,
            boolean expired,
            LocalDate effectiveStartDate,
            LocalDate effectiveEndDate) {
    }

    public Result resolve(Snapshot snapshot) {
        return resolve(snapshot, LocalDate.now());
    }

    public Result resolve(Snapshot snapshot, LocalDate today) {
        LocalDate effectiveStart = snapshot.startDate() != null
                ? snapshot.startDate() : snapshot.recordStart();
        LocalDate effectiveEnd = snapshot.endDate() != null
                ? snapshot.endDate() : snapshot.recordEnd();

        if (snapshot.statusManual()) {
            EffectiveTripStatus manual = snapshot.storedStatus() == TripStatus.COMPLETED
                    ? EffectiveTripStatus.COMPLETED : EffectiveTripStatus.PLANNED;
            return new Result(manual, false, effectiveStart, effectiveEnd);
        }

        if (snapshot.hasRecords()) {
            if (snapshot.startDate() != null && snapshot.endDate() != null) {
                if (today.isAfter(snapshot.endDate())) {
                    return new Result(EffectiveTripStatus.COMPLETED, false, effectiveStart, effectiveEnd);
                }
                if (!today.isBefore(snapshot.startDate())) {
                    return new Result(EffectiveTripStatus.IN_PROGRESS, false, effectiveStart, effectiveEnd);
                }
                return new Result(EffectiveTripStatus.PLANNED, false, effectiveStart, effectiveEnd);
            }
            if (effectiveEnd != null) {
                if (!today.isBefore(effectiveEnd)) {
                    return new Result(EffectiveTripStatus.COMPLETED, false, effectiveStart, effectiveEnd);
                }
                if (effectiveStart != null && !today.isBefore(effectiveStart)) {
                    return new Result(EffectiveTripStatus.IN_PROGRESS, false, effectiveStart, effectiveEnd);
                }
            }
        }

        boolean expired = snapshot.endDate() != null && today.isAfter(snapshot.endDate())
                && !snapshot.hasRecords();
        return new Result(EffectiveTripStatus.PLANNED, expired, effectiveStart, effectiveEnd);
    }
}