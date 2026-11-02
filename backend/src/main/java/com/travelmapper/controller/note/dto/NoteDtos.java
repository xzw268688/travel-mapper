package com.travelmapper.controller.note.dto;

import com.travelmapper.repository.enums.Visibility;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

public final class NoteDtos {

    private NoteDtos() {
    }

    @Data
    public static class CreateRequest {
        @NotBlank
        @Size(max = 50)
        private String title;
        @NotBlank
        private String content;
        private List<String> images;
        private List<String> videos;
        private String coverImage;
        @NotBlank
        private String countryCode;
        @NotBlank
        private String cityCode;
        private Long itineraryId;
        private Long tripId;
        private Visibility visibility;
    }

    @Data
    public static class UpdateRequest {
        @Size(max = 50)
        private String title;
        private String content;
        private List<String> images;
        private List<String> videos;
        private String coverImage;
        private Visibility visibility;
    }

    public record ImageView(String objectKey, String url) {
    }

    public record VideoView(String objectKey, String url) {
    }

    public record ShareState(boolean active, String token, String url, LocalDateTime expireAt) {
    }

    public record Response(
            Long id,
            String title,
            String content,
            List<ImageView> images,
            List<VideoView> videos,
            String coverImage,
            String countryCode,
            String countryName,
            String cityCode,
            String cityName,
            boolean cityDisabled,
            Long itineraryId,
            Long tripId,
            String tripName,
            String visibility,
            ShareState share,
            boolean takedown,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
    }

    public record CreateResponse(
            Long id,
            Long tripId,
            boolean tripInherited,
            String countryStatusAfter) {
    }

    public record DeleteResponse(
            Long noteId,
            String countryStatusAfter,
            int sharesRevoked) {
    }

    @Data
    public static class TakedownRequest {
        private String reason;
    }

    public record TakedownResponse(Long noteId, boolean takedown, int sharesRevoked) {
    }

    public record RestoreResponse(Long noteId, boolean takedown) {
    }
}
