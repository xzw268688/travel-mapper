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

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp");
    private static final long MAX_SIZE = 5L * 1024 * 1024;

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
        if (!"NOTE_IMAGE".equals(request.getBizType())) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "bizType 仅支持 NOTE_IMAGE");
        }
        String contentType = request.getContentType().toLowerCase(Locale.ROOT);
        if (!ALLOWED_TYPES.contains(contentType)) {
            throw new BusinessException(ErrorCode.IMAGE_TYPE_UNSUPPORTED);
        }
        if (request.getSize() == null || request.getSize() <= 0) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "size 必填");
        }
        if (request.getSize() > MAX_SIZE) {
            throw new BusinessException(ErrorCode.IMAGE_SIZE_LIMIT);
        }
        String objectKey = objectKeyGenerator.generate(CurrentUser.id(), contentType);
        StorageProvider.PresignedUpload upload = storageUrlService.presignUpload(objectKey, contentType);
        return ApiResponse.ok(new UploadDtos.PresignResponse(
                upload.objectKey(), upload.uploadUrl(), upload.method(),
                upload.headers(), upload.expireAt()));
    }
}
