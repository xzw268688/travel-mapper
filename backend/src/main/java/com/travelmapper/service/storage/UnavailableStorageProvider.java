package com.travelmapper.service.storage;

import com.travelmapper.common.api.BusinessException;
import com.travelmapper.common.api.ErrorCode;

import java.util.List;

public class UnavailableStorageProvider implements StorageProvider {

    private final String reason;

    public UnavailableStorageProvider(String reason) {
        this.reason = reason;
    }

    @Override
    public PresignedUpload presignUpload(String objectKey, String contentType, long expireSeconds) {
        throw unavailable();
    }

    @Override
    public String presignDownload(String objectKey, long expireSeconds) {
        throw unavailable();
    }

    @Override
    public void delete(String objectKey) {
        throw unavailable();
    }

    @Override
    public boolean exists(String objectKey) {
        return false;
    }

    @Override
    public List<StoredObject> list(String prefix) {
        return List.of();
    }

    @Override
    public String providerName() {
        return "unconfigured";
    }

    private BusinessException unavailable() {
        return new BusinessException(ErrorCode.STORAGE_UNAVAILABLE, "存储服务未配置：" + reason);
    }
}
