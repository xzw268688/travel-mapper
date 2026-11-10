package com.travelmapper.controller.map;

import com.travelmapper.common.api.ApiResponse;
import com.travelmapper.common.security.CurrentUser;
import com.travelmapper.controller.map.dto.CountryDrawerResponse;
import com.travelmapper.controller.map.dto.MapStatsResponse;
import com.travelmapper.service.map.MapService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Map")
@RestController
@RequestMapping("/api/map")
public class MapController {

    private final MapService mapService;

    public MapController(MapService mapService) {
        this.mapService = mapService;
    }

    @GetMapping("/stats")
    public ApiResponse<MapStatsResponse> stats() {
        return ApiResponse.ok(mapService.stats(CurrentUser.id()));
    }

    @GetMapping("/country/{countryCode}")
    public ApiResponse<CountryDrawerResponse> country(@PathVariable String countryCode) {
        return ApiResponse.ok(mapService.drawer(CurrentUser.id(), countryCode));
    }
}
