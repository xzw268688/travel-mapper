package com.travelmapper.controller.admin.dto;

import java.time.LocalDateTime;
import java.util.List;

public final class MonitorDtos {

    private MonitorDtos() {
    }

    public record OnlineUserView(
            Long userId,
            String username,
            String nickname,
            String role,
            String ip,
            String userAgent,
            LocalDateTime lastActiveAt) {
    }

    public record DiskView(String name, String mount, long totalBytes, long usableBytes) {
    }

    public record JvmView(
            long heapUsedBytes,
            long heapMaxBytes,
            long heapCommittedBytes,
            long nonHeapUsedBytes,
            int threadCount,
            int peakThreadCount,
            long gcCount,
            long gcTimeMs,
            long uptimeMs,
            String javaVersion,
            String javaVendor,
            LocalDateTime startTime) {
    }

    public record ServerMetrics(
            String osName,
            String osVersion,
            String osArch,
            int availableProcessors,
            int logicalCpuCount,
            int physicalCpuCount,
            double systemCpuUsage,
            double processCpuUsage,
            long totalMemoryBytes,
            long freeMemoryBytes,
            long systemUptimeSeconds,
            List<DiskView> disks,
            JvmView jvm,
            boolean partial) {
    }

    public record DatabaseMetrics(
            int activeConnections,
            int idleConnections,
            int totalConnections,
            int waitingThreads,
            int maxPoolSize,
            int minIdle,
            long connectionTimeoutMs,
            long acquireCount,
            double acquireAvgMs,
            double acquireMaxMs,
            boolean partial) {
    }

    public record CacheOverview(
            String usedMemory,
            String maxMemory,
            long connectedClients,
            double hitRate,
            long keyspaceHits,
            long keyspaceMisses,
            long dbKeys) {
    }

    public record CacheGroupView(
            String key,
            String label,
            long keyCount,
            Long minTtlSeconds,
            Long maxTtlSeconds,
            boolean truncated,
            boolean clearable) {
    }

    public record CacheMetrics(CacheOverview overview, List<CacheGroupView> groups) {
    }

    public record CacheClearResponse(String group, long deleted) {
    }

    public record StorageMetrics(
            String provider,
            String endpoint,
            String bucket,
            boolean pathStyle,
            boolean configured,
            boolean reachable,
            long latencyMs,
            String message) {
    }

    public record StorageStats(
            long objectCount,
            long totalBytes,
            boolean truncated,
            boolean available,
            LocalDateTime measuredAt) {
    }
}