package com.travelmapper.service.monitor;

import com.travelmapper.common.time.TimeProvider;
import com.travelmapper.controller.admin.dto.MonitorDtos;
import com.travelmapper.service.storage.StorageConfig;
import com.travelmapper.service.storage.StorageProvider;
import com.travelmapper.service.storage.StorageProviderHolder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;

@Service
public class AdminStorageMonitorService {

    private static final int MAX_OBJECTS = 10_000;
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    private final StorageProviderHolder providerHolder;
    private final TimeProvider timeProvider;
    private volatile CachedStats cached;

    public AdminStorageMonitorService(StorageProviderHolder providerHolder, TimeProvider timeProvider) {
        this.providerHolder = providerHolder;
        this.timeProvider = timeProvider;
    }

    public MonitorDtos.StorageMetrics metrics() {
        StorageConfig config = providerHolder.loadConfig();
        StorageProvider provider = providerHolder.get();
        StorageProvider.ProbeResult probe = provider.probe();
        return new MonitorDtos.StorageMetrics(
                provider.providerName(),
                config.endpoint(),
                config.bucket(),
                config.pathStyle(),
                config.isConfigured(),
                probe.reachable(),
                probe.latencyMs(),
                probe.message());
    }

    public MonitorDtos.StorageStats stats() {
        StorageConfig config = providerHolder.loadConfig();
        String configKey = config.endpoint() + "|" + config.bucket();
        CachedStats current = cached;
        if (current != null && current.configKey().equals(configKey) && !current.expired()) {
            return current.stats();
        }
        synchronized (this) {
            current = cached;
            if (current != null && current.configKey().equals(configKey) && !current.expired()) {
                return current.stats();
            }
            LocalDateTime now = timeProvider.now();
            if (!config.isConfigured()) {
                return new MonitorDtos.StorageStats(0, 0, false, false, now);
            }
            StorageProvider provider = providerHolder.get();
            StorageProvider.ProbeResult probe = provider.probe();
            if (!probe.reachable()) {
                return new MonitorDtos.StorageStats(0, 0, false, false, now);
            }
            StorageProvider.Stats stats = provider.stats(MAX_OBJECTS);
            MonitorDtos.StorageStats view = new MonitorDtos.StorageStats(
                    stats.objectCount(), stats.totalBytes(), stats.truncated(), true, now);
            cached = new CachedStats(configKey, view,
                    System.currentTimeMillis() + CACHE_TTL.toMillis());
            return view;
        }
    }

    private record CachedStats(String configKey, MonitorDtos.StorageStats stats, long expireAt) {
        boolean expired() {
            return System.currentTimeMillis() > expireAt;
        }
    }
}