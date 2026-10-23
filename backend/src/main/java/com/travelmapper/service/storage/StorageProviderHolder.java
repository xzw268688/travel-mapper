package com.travelmapper.service.storage;

import com.travelmapper.service.config.ConfigKeys;
import com.travelmapper.service.config.ConfigService;
import com.travelmapper.service.config.ReloadableProvider;
import jakarta.annotation.PreDestroy;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;

@Slf4j
@Service
public class StorageProviderHolder implements ReloadableProvider {

    private final ConfigService configService;
    private volatile StorageProvider current = new UnavailableStorageProvider("not initialized");

    public StorageProviderHolder(ConfigService configService) {
        this.configService = configService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        reloadQuietly();
    }

    @Override
    public synchronized void reload() {
        StorageConfig config = loadConfig();
        if (!config.isConfigured()) {
            swap(new UnavailableStorageProvider("access-key/secret-key/bucket/endpoint missing"));
            return;
        }
        URI endpoint = URI.create(config.endpoint());
        if (endpoint.getScheme() == null
                || (!"http".equalsIgnoreCase(endpoint.getScheme()) && !"https".equalsIgnoreCase(endpoint.getScheme()))) {
            throw new IllegalArgumentException("storage.endpoint must be http/https");
        }
        S3CompatibleStorageProvider created = new S3CompatibleStorageProvider(config);
        swap(created);
    }

    private void reloadQuietly() {
        try {
            reload();
        } catch (RuntimeException ex) {
            log.warn("storage provider unavailable at startup: {}", ex.getMessage());
            swap(new UnavailableStorageProvider(ex.getMessage()));
        }
    }

    private void swap(StorageProvider next) {
        StorageProvider old = current;
        current = next;
        close(old);
    }

    private void close(StorageProvider provider) {
        if (provider instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception ex) {
                log.debug("failed to close storage provider", ex);
            }
        }
    }

    public StorageProvider get() {
        return current;
    }

    public StorageConfig loadConfig() {
        return new StorageConfig(
                configService.get(ConfigKeys.STORAGE_PROVIDER, "minio"),
                configService.get(ConfigKeys.STORAGE_ENDPOINT, ""),
                configService.get(ConfigKeys.STORAGE_REGION, ""),
                configService.get(ConfigKeys.STORAGE_BUCKET, ""),
                configService.get(ConfigKeys.STORAGE_ACCESS_KEY, ""),
                configService.get(ConfigKeys.STORAGE_SECRET_KEY, ""),
                configService.getBoolean(ConfigKeys.STORAGE_PATH_STYLE, true),
                configService.get(ConfigKeys.STORAGE_PUBLIC_DOMAIN, ""));
    }

    @PreDestroy
    public void shutdown() {
        close(current);
    }
}
