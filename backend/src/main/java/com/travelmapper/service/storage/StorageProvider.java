package com.travelmapper.service.storage;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public interface StorageProvider {

    PresignedUpload presignUpload(String objectKey, String contentType, long expireSeconds);

    String presignDownload(String objectKey, long expireSeconds);

    void delete(String objectKey);

    boolean exists(String objectKey);

    List<StoredObject> list(String prefix);

    /**
     * 轻量健康探测：只检查桶是否可访问，不列举对象。
     */
    ProbeResult probe();

    /**
     * 按需统计对象数与总大小；超过 maxObjects 时停止并标记 truncated。
     */
    Stats stats(int maxObjects);

    String providerName();

    record PresignedUpload(String objectKey, String uploadUrl, String method,
                           Map<String, String> headers, Instant expireAt) {
    }

    record StoredObject(String key, Instant lastModified, long size) {
    }

    record ProbeResult(boolean reachable, long latencyMs, String message) {
    }

    record Stats(long objectCount, long totalBytes, boolean truncated) {
    }
}
