package com.travelmapper.controller.trip.dto;

import java.time.LocalDate;

/** 时间线节点：按归档起始日期排序，点击进入归档详情。 */
public record TripTimelineView(
        Long id,
        String name,
        String countryCode,
        String countryName,
        LocalDate startDate,
        LocalDate endDate,
        String status,
        int itineraryCount,
        int noteCount,
        int expenseCount,
        String expenseCny) {
}
