package com.travelmapper.controller.itinerary.dto;

import com.travelmapper.service.search.GuideLink;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class ItineraryDtos {

    private ItineraryDtos() {
    }

    @Data
    public static class SaveRequest {
        @NotBlank
        private String countryCode;
        @NotBlank
        @Size(max = 50)
        private String title;
        @NotNull
        private LocalDate startDate;
        @NotNull
        private LocalDate endDate;
        private Long tripId;
        @Size(max = 30)
        private String tripName;
        private List<GuideLink> guides;
        private GenerateItineraryResponse.Preparations preparations;
        @NotEmpty
        @Valid
        private List<DayRequest> days;
    }

    @Data
    public static class DayRequest {
        private int dayIndex;
        @NotNull
        private LocalDate date;
        @NotBlank
        private String cityCode;
        @Valid
        private List<SpotRequest> spots;
    }

    @Data
    public static class SpotRequest {
        @NotBlank
        @Size(max = 50)
        private String name;
        @Size(max = 200)
        private String description;
        @Size(max = 20)
        private String suggestedDuration;
        private String source;
    }

    public record SaveResponse(
            Long itineraryId,
            Long tripId,
            String tripName,
            boolean tripCreated,
            String countryStatusAfter,
            int manualSpotCount) {
    }

    public record Response(
            Long id,
            Long tripId,
            String tripName,
            String countryCode,
            String countryName,
            String title,
            LocalDate startDate,
            LocalDate endDate,
            String status,
            boolean expired,
            List<GuideLink> guides,
            GenerateItineraryResponse.Preparations preparations,
            List<DayView> days,
            int manualSpotCount,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
    }

    public record DayView(
            Long dayId,
            int dayIndex,
            LocalDate date,
            String cityCode,
            String cityName,
            boolean cityDisabled,
            List<SpotView> spots) {
    }

    public record SpotView(
            Long spotId,
            int sortOrder,
            String source,
            String name,
            String description,
            String suggestedDuration) {
    }

    @Data
    public static class UpdateRequest {
        @Size(max = 50)
        private String title;
        private LocalDate startDate;
        private LocalDate endDate;
    }

    public record DeleteResponse(Long itineraryId, String countryStatusAfter) {
    }

    @Data
    public static class RegenerateRequest {
        @NotBlank
        private String requestId;
        private boolean keepManualSpots;
        @Size(max = 200)
        private String preference;
    }

    public record RegenerateResponse(
            Response itinerary,
            int mergedManualSpots,
            List<String> mergeWarnings,
            List<String> warnings,
            GenerateItineraryResponse.QuotaInfo quota) {
    }

    @Data
    public static class AddSpotRequest {
        @NotBlank
        @Size(max = 50)
        private String name;
        @Size(max = 200)
        private String description;
        @Size(max = 20)
        private String suggestedDuration;
        private Integer insertIndex;
    }

    public record AddSpotResponse(
            Long spotId,
            Long dayId,
            int sortOrder,
            String source,
            int manualSpotCount) {
    }

    @Data
    public static class UpdateSpotRequest {
        @NotBlank
        @Size(max = 50)
        private String name;
        @Size(max = 200)
        private String description;
        @Size(max = 20)
        private String suggestedDuration;
    }

    public record UpdateSpotResponse(Long spotId, String source, String name) {
    }

    public record DeleteSpotResponse(Long spotId, int manualSpotCount) {
    }

    @Data
    public static class MoveSpotRequest {
        @NotNull
        private Long targetDayId;
        private Integer targetIndex;
    }

    public record AffectedDay(Long dayId, List<Long> spotIds) {
    }

    public record MoveSpotResponse(
            Long spotId,
            Long fromDayId,
            Long toDayId,
            List<AffectedDay> affectedDays) {
    }

    @Data
    public static class AddDayRequest {
        @NotNull
        private Integer insertIndex;
        @NotNull
        private LocalDate date;
        @NotBlank
        private String cityCode;
    }

    @Data
    public static class UpdateDayRequest {
        private LocalDate date;
        private String cityCode;
    }

    public record DeleteDayResponse(
            Long itineraryId,
            int deletedSpots,
            int dayCount,
            LocalDate startDate,
            LocalDate endDate) {
    }
}
