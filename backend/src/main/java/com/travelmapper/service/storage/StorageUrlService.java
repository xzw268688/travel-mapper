package com.travelmapper.service.storage;

import com.travelmapper.common.api.BusinessException;
import com.travelmapper.common.api.ErrorCode;
import com.travelmapper.common.config.AppProperties;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

@Service
public class StorageUrlService {

    private final StorageProviderHolder holder;
    private final AppProperties properties;

    public StorageUrlService(StorageProviderHolder holder, AppProperties properties) {
        this.holder = holder;
        this.properties = properties;
    }

    public StorageProvider.PresignedUpload presignUpload(String objectKey, String contentType) {
        return presignUpload(objectKey, contentType,
                properties.getStorage().getUploadTtlSeconds());
    }

    public StorageProvider.PresignedUpload presignUpload(String objectKey, String contentType,
                                                         long ttlSeconds) {
        return holder.get().presignUpload(objectKey, contentType, ttlSeconds);
    }

    public String presignDownload(String objectKey) {
        return holder.get().presignDownload(objectKey,
                properties.getStorage().getDownloadTtlSeconds());
    }

    public String presignShareDownload(String objectKey, Instant tokenExpireAt) {
        long ttl = properties.getStorage().getShareMaxTtlSeconds();
        if (tokenExpireAt != null) {
            long remaining = Duration.between(Instant.now(), tokenExpireAt).getSeconds();
            if (remaining <= 0) {
                throw new BusinessException(ErrorCode.SHARE_INVALID);
            }
            ttl = Math.min(ttl, remaining);
        }
        return holder.get().presignDownload(objectKey, ttl);
    }
}
