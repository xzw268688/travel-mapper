package com.travelmapper.service.storage;

public record StorageTestResult(boolean ok, long latencyMs, String message) {
}