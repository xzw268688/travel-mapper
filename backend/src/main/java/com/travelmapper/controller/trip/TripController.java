package com.travelmapper.controller.trip;

import com.travelmapper.common.api.ApiResponse;
import com.travelmapper.common.security.CurrentUser;
import com.travelmapper.controller.trip.dto.TripDeleteResponse;
import com.travelmapper.controller.trip.dto.TripDetailView;
import com.travelmapper.controller.trip.dto.TripRenameRequest;
import com.travelmapper.controller.trip.dto.TripRenameResponse;
import com.travelmapper.controller.trip.dto.TripStatusRequest;
import com.travelmapper.controller.trip.dto.TripStatusResponse;
import com.travelmapper.controller.trip.dto.TripTimelineView;
import com.travelmapper.controller.trip.dto.TripView;
import com.travelmapper.service.trip.TripService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Trips")
@RestController
@RequestMapping("/api/trips")
public class TripController {

    private final TripService tripService;

    public TripController(TripService tripService) {
        this.tripService = tripService;
    }

    @GetMapping
    public ApiResponse<List<TripView>> list(@RequestParam String countryCode) {
        return ApiResponse.ok(tripService.list(CurrentUser.id(), countryCode));
    }

    @GetMapping("/timeline")
    public ApiResponse<List<TripTimelineView>> timeline() {
        return ApiResponse.ok(tripService.timeline(CurrentUser.id()));
    }

    @PutMapping("/{id}/status")
    public ApiResponse<TripStatusResponse> updateStatus(
            @PathVariable Long id, @Valid @RequestBody TripStatusRequest request) {
        return ApiResponse.ok(tripService.updateStatus(CurrentUser.id(), id, request.getStatus()));
    }

    @DeleteMapping("/{id}/status")
    public ApiResponse<TripStatusResponse> resetStatus(@PathVariable Long id) {
        return ApiResponse.ok(tripService.resetStatus(CurrentUser.id(), id));
    }

    @GetMapping("/{id}")
    public ApiResponse<TripDetailView> detail(@PathVariable Long id) {
        return ApiResponse.ok(tripService.detail(CurrentUser.id(), id));
    }

    @PutMapping("/{id}")
    public ApiResponse<TripRenameResponse> rename(@PathVariable Long id,
                                                  @Valid @RequestBody TripRenameRequest request) {
        return ApiResponse.ok(tripService.rename(CurrentUser.id(), id, request.getName()));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<TripDeleteResponse> delete(
            @PathVariable Long id,
            @RequestParam(defaultValue = "false") boolean force) {
        return ApiResponse.ok(tripService.delete(CurrentUser.id(), id, force));
    }
}
