package com.travelmapper.controller.upload.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.Instant;
import java.util.Map;

public final class UploadDtos {

    private UploadDtos() {
    }

    @Data
    public static class PresignRequest {
        @NotBlank
        private String bizType;
        @NotBlank
        private String contentType;
        @NotNull
        private Long size;
        /** 为已上传图片生成缩略图时传入原图 objectKey。 */
        private String thumbOf;
    }

    public record PresignResponse(
            String objectKey,
            String uploadUrl,
            String method,
            Map<String, String> headers,
            Instant expireAt) {
    }
}
