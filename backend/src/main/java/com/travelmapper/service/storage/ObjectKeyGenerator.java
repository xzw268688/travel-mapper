package com.travelmapper.service.storage;

import com.travelmapper.common.api.BusinessException;
import com.travelmapper.common.api.ErrorCode;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Component
public class ObjectKeyGenerator {

    private static final Map<String, String> CONTENT_TYPE_EXTENSIONS = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp",
            "video/mp4", "mp4",
            "video/webm", "webm");

    public String generate(Long userId, String contentType) {
        String extension = CONTENT_TYPE_EXTENSIONS.get(contentType == null
                ? "" : contentType.toLowerCase(Locale.ROOT));
        if (extension == null) {
            String type = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
            throw new BusinessException(type.startsWith("video/")
                    ? ErrorCode.VIDEO_TYPE_UNSUPPORTED
                    : ErrorCode.IMAGE_TYPE_UNSUPPORTED);
        }
        LocalDate today = LocalDate.now();
        return "note/%d/%04d/%02d/%s.%s".formatted(
                userId, today.getYear(), today.getMonthValue(), UUID.randomUUID(), extension);
    }
}
