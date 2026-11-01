package com.travelmapper.controller.admin;

import com.travelmapper.common.api.ApiResponse;
import com.travelmapper.service.trip.TripReclusterService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin Trips")
@RestController
@RequestMapping("/api/admin/trips")
public class AdminTripController {

    private final TripReclusterService reclusterService;

    public AdminTripController(TripReclusterService reclusterService) {
        this.reclusterService = reclusterService;
    }

    /** 一次性重算：按国家 + 30 天窗口合并旅行并重挂游记/消费。 */
    @PostMapping("/recluster")
    public ApiResponse<TripReclusterService.Result> recluster() {
        return ApiResponse.ok(reclusterService.recluster());
    }
}
