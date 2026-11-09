package com.travelmapper.controller.admin.dto;

public record ReportStatsView(
        long total,
        long pendingCount,
        long acceptedCount,
        long rejectedCount) {
}