package com.travelmapper.controller.note.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

public final class ShareDtos {

    private ShareDtos() {
    }

    @Data
    public static class ShareRequest {
        private Integer expireDays;
    }

    public record ShareResponse(
            String token,
            String url,
            String fullUrl,
            LocalDateTime expireAt,
            boolean created) {
    }

    public record RevokeResponse(int revoked) {
    }

    public record SharePageResponse(
            String title,
            String summary,
            String coverImageUrl,
            List<String> images,
            String countryCode,
            String countryName,
            String cityCode,
            String cityName,
            LocalDateTime createdAt,
            String nicknameMasked) {
    }
}
