package com.travelmapper.service.note;

import com.travelmapper.common.api.BusinessException;
import com.travelmapper.common.api.ErrorCode;
import com.travelmapper.service.storage.StorageProvider;
import com.travelmapper.service.storage.StorageProviderHolder;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 保存游记前的媒体完整性校验：对象必须位于当前用户目录，且已真实上传。
 */
@Service
public class NoteMediaValidator {

    private static final String PREFIX = "note/";

    private final StorageProviderHolder storage;

    public NoteMediaValidator(StorageProviderHolder storage) {
        this.storage = storage;
    }

    public void validate(Long userId, List<String> images, List<String> videos) {
        boolean hasMedia = (images != null && !images.isEmpty())
                || (videos != null && !videos.isEmpty());
        if (!hasMedia) {
            return;
        }
        StorageProvider provider = storage.get();
        if ("unconfigured".equals(provider.providerName())) {
            throw new BusinessException(ErrorCode.STORAGE_UNAVAILABLE, "存储服务未配置，无法保存媒体");
        }
        validateKeys(userId, images, provider);
        validateKeys(userId, videos, provider);
    }

    private void validateKeys(Long userId, List<String> keys, StorageProvider provider) {
        if (keys == null) {
            return;
        }
        String expectedPrefix = PREFIX + userId + "/";
        for (String key : keys) {
            if (key == null || !key.startsWith(expectedPrefix) || key.contains("..")) {
                throw new BusinessException(ErrorCode.MEDIA_OBJECT_INVALID);
            }
            if (!provider.exists(key)) {
                throw new BusinessException(ErrorCode.MEDIA_OBJECT_INVALID);
            }
        }
    }
}