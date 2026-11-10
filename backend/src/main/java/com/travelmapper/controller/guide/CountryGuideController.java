package com.travelmapper.controller.guide;

import com.travelmapper.common.api.ApiResponse;
import com.travelmapper.common.security.CurrentUser;
import com.travelmapper.controller.guide.dto.CountryGuideDtos;
import com.travelmapper.controller.guide.dto.CountryGuideRequest;
import com.travelmapper.service.guide.CountryGuideService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Tag(name = "Country Guide")
@RestController
@RequestMapping("/api/country-guide")
public class CountryGuideController {

    private final CountryGuideService guideService;

    public CountryGuideController(CountryGuideService guideService) {
        this.guideService = guideService;
    }

    @GetMapping("/{countryCode}")
    public ApiResponse<CountryGuideDtos.CacheView> cached(@PathVariable String countryCode) {
        return ApiResponse.ok(guideService.cached(countryCode));
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@Valid @RequestBody CountryGuideRequest request) {
        return guideService.stream(CurrentUser.id(), request.getRequestId(), request.getCountryCode());
    }
}