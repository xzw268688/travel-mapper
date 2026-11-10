package com.travelmapper.controller.admin.dto;

public record FeedbackStatsView(
        long total,
        long newCount,
        long processingCount,
        long resolvedCount) {
}