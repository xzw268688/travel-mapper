package com.travelmapper.controller.prep.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

public final class OfficialLinkDtos {

    private OfficialLinkDtos() {
    }

    public record PublicResponse(
            String countryCode,
            boolean fallback,
            List<PublicLink> links,
            String searchUrl) {
    }

    public record PublicLink(String title, String url) {
    }

    public record AdminView(
            Long id,
            String countryCode,
            String title,
            String url,
            Integer sortOrder) {
    }

    @Data
    public static class SaveRequest {
        @NotBlank
        private String countryCode;
        @NotBlank
        @Size(max = 64)
        private String title;
        @NotBlank
        @Size(max = 500)
        private String url;
        private Integer sortOrder;
    }
}
