package com.travelmapper.controller.itinerary;

import com.travelmapper.common.api.ApiResponse;
import com.travelmapper.common.security.CurrentUser;
import com.travelmapper.controller.itinerary.dto.ItineraryDtos;
import com.travelmapper.service.itinerary.ItineraryService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Itinerary")
@RestController
@RequestMapping("/api/itinerary")
public class ItineraryController {

    private final ItineraryService itineraryService;

    public ItineraryController(ItineraryService itineraryService) {
        this.itineraryService = itineraryService;
    }

    @PostMapping("/save")
    public ApiResponse<ItineraryDtos.SaveResponse> save(
            @Valid @RequestBody ItineraryDtos.SaveRequest request) {
        return ApiResponse.ok(itineraryService.save(CurrentUser.id(), request));
    }

    @GetMapping("/{id}")
    public ApiResponse<ItineraryDtos.Response> detail(@PathVariable Long id) {
        return ApiResponse.ok(itineraryService.detail(CurrentUser.id(), id));
    }

    @PutMapping("/{id}")
    public ApiResponse<ItineraryDtos.Response> update(
            @PathVariable Long id, @Valid @RequestBody ItineraryDtos.UpdateRequest request) {
        return ApiResponse.ok(itineraryService.update(CurrentUser.id(), id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<ItineraryDtos.DeleteResponse> delete(@PathVariable Long id) {
        return ApiResponse.ok(itineraryService.delete(CurrentUser.id(), id));
    }

    @PostMapping("/{id}/regenerate")
    public ApiResponse<ItineraryDtos.RegenerateResponse> regenerate(
            @PathVariable Long id, @Valid @RequestBody ItineraryDtos.RegenerateRequest request) {
        return ApiResponse.ok(itineraryService.regenerate(CurrentUser.id(), id, request));
    }

    @PostMapping("/{id}/days/{dayId}/spots")
    public ApiResponse<ItineraryDtos.AddSpotResponse> addSpot(
            @PathVariable Long id, @PathVariable Long dayId,
            @Valid @RequestBody ItineraryDtos.AddSpotRequest request) {
        return ApiResponse.ok(itineraryService.addSpot(CurrentUser.id(), id, dayId, request));
    }

    @PutMapping("/spots/{spotId}")
    public ApiResponse<ItineraryDtos.UpdateSpotResponse> updateSpot(
            @PathVariable Long spotId, @Valid @RequestBody ItineraryDtos.UpdateSpotRequest request) {
        return ApiResponse.ok(itineraryService.updateSpot(CurrentUser.id(), spotId, request));
    }

    @DeleteMapping("/spots/{spotId}")
    public ApiResponse<ItineraryDtos.DeleteSpotResponse> deleteSpot(@PathVariable Long spotId) {
        return ApiResponse.ok(itineraryService.deleteSpot(CurrentUser.id(), spotId));
    }

    @PutMapping("/spots/{spotId}/move")
    public ApiResponse<ItineraryDtos.MoveSpotResponse> moveSpot(
            @PathVariable Long spotId, @Valid @RequestBody ItineraryDtos.MoveSpotRequest request) {
        return ApiResponse.ok(itineraryService.moveSpot(CurrentUser.id(), spotId, request));
    }

    @PostMapping("/{id}/days")
    public ApiResponse<ItineraryDtos.Response> addDay(
            @PathVariable Long id, @Valid @RequestBody ItineraryDtos.AddDayRequest request) {
        return ApiResponse.ok(itineraryService.addDay(CurrentUser.id(), id, request));
    }

    @PutMapping("/{id}/days/{dayId}")
    public ApiResponse<ItineraryDtos.Response> updateDay(
            @PathVariable Long id, @PathVariable Long dayId,
            @RequestBody ItineraryDtos.UpdateDayRequest request) {
        return ApiResponse.ok(itineraryService.updateDay(CurrentUser.id(), id, dayId, request));
    }

    @DeleteMapping("/{id}/days/{dayId}")
    public ApiResponse<ItineraryDtos.DeleteDayResponse> deleteDay(
            @PathVariable Long id, @PathVariable Long dayId) {
        return ApiResponse.ok(itineraryService.deleteDay(CurrentUser.id(), id, dayId));
    }
}
