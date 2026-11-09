package com.travelmapper.service.config;

public record AlertConfig(boolean enabled, String webhookUrl, int minIntervalSeconds) {
}