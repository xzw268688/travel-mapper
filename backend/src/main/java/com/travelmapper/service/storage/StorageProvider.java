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

    String providerName();

    record PresignedUpload(String objectKey, String uploadUrl, String method,
                           Map<String, String> headers, Instant expireAt) {
    }

    record StoredObject(String key, Instant lastModified, long size) {
    }
}
