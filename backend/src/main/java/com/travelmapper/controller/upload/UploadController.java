package com.travelmapper.controller.upload;

import com.travelmapper.common.api.ApiResponse;
import com.travelmapper.common.api.BusinessException;
import com.travelmapper.common.api.ErrorCode;
import com.travelmapper.common.security.CurrentUser;
import com.travelmapper.controller.upload.dto.UploadDtos;
import com.travelmapper.service.storage.ObjectKeyGenerator;
import com.travelmapper.service.storage.StorageProvider;
import com.travelmapper.service.storage.StorageUrlService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;
import java.util.Set;

@Tag(name = "Upload")
@RestController
@RequestMapping("/api/upload")
public class UploadController {

    private static final Set<String> IMAGE_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp");
    private static final Set<String> VIDEO_TYPES = Set.of(
            "video/mp4", "video/webm", "video/quicktime");
    private static final long MAX_IMAGE_SIZE = 5L * 1024 * 1024;
    private static final long MAX_THUMB_SIZE = 512L * 1024;
    private static final long MAX_VIDEO_SIZE = 100L * 1024 * 1024;
    private static final String THUMB_SUFFIX = ".thumb.jpg";
    private static final long IMAGE_TTL_SECONDS = 300;
    private static final long VIDEO_TTL_SECONDS = 1800;

    private final ObjectKeyGenerator objectKeyGenerator;
    private final StorageUrlService storageUrlService;

    public UploadController(ObjectKeyGenerator objectKeyGenerator,
                            StorageUrlService storageUrlService) {
        this.objectKeyGenerator = objectKeyGenerator;
        this.storageUrlService = storageUrlService;
    }

    @PostMapping("/presign")
    public ApiResponse<UploadDtos.PresignResponse> presign(
            @Valid @RequestBody UploadDtos.PresignRequest request) {
        String bizType = request.getBizType();
        if (!"NOTE_IMAGE".equals(bizType) && !"NOTE_VIDEO".equals(bizType)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID,
                    "bizType 仅支持 NOTE_IMAGE / NOTE_VIDEO");
        }
        String contentType = request.getContentType().toLowerCase(Locale.ROOT);
        if (request.getSize() == null || request.getSize() <= 0) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "size 必填");
        }
        boolean video = "NOTE_VIDEO".equals(bizType);
        if (request.getThumbOf() != null && !request.getThumbOf().isBlank()) {
            return ApiResponse.ok(presignThumbnail(request.getThumbOf(), video, contentType,
                    request.getSize()));
        }
        if (video) {
            if (!VIDEO_TYPES.contains(contentType)) {
                throw new BusinessException(ErrorCode.VIDEO_TYPE_UNSUPPORTED);
            }
            if (request.getSize() > MAX_VIDEO_SIZE) {
                throw new BusinessException(ErrorCode.VIDEO_SIZE_LIMIT);
            }
        } else {
            if (!IMAGE_TYPES.contains(contentType)) {
                throw new BusinessException(ErrorCode.IMAGE_TYPE_UNSUPPORTED);
            }
            if (request.getSize() > MAX_IMAGE_SIZE) {
                throw new BusinessException(ErrorCode.IMAGE_SIZE_LIMIT);
            }
        }
        String objectKey = objectKeyGenerator.generate(CurrentUser.id(), contentType);
        StorageProvider.PresignedUpload upload = storageUrlService.presignUpload(
                objectKey, contentType, video ? VIDEO_TTL_SECONDS : IMAGE_TTL_SECONDS);
        return ApiResponse.ok(new UploadDtos.PresignResponse(
                upload.objectKey(), upload.uploadUrl(), upload.method(),
                upload.headers(), upload.expireAt()));
    }

    private UploadDtos.PresignResponse presignThumbnail(String rawThumbOf, boolean video,
                                                        String contentType, long size) {
        if (video) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "缩略图仅支持图片");
        }
        if (!"image/jpeg".equals(contentType)) {
            throw new BusinessException(ErrorCode.IMAGE_TYPE_UNSUPPORTED);
        }
        if (size > MAX_THUMB_SIZE) {
            throw new BusinessException(ErrorCode.IMAGE_SIZE_LIMIT);
        }
        String thumbOf = rawThumbOf.trim();
        String prefix = "note/" + CurrentUser.id() + "/";
        if (!thumbOf.startsWith(prefix) || thumbOf.contains("..")
                || thumbOf.endsWith(THUMB_SUFFIX)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "thumbOf 必须属于当前用户");
        }
        StorageProvider.PresignedUpload upload = storageUrlService.presignUpload(
                thumbOf + THUMB_SUFFIX, "image/jpeg", IMAGE_TTL_SECONDS);
        return new UploadDtos.PresignResponse(
                upload.objectKey(), upload.uploadUrl(), upload.method(),
                upload.headers(), upload.expireAt());
    }
}