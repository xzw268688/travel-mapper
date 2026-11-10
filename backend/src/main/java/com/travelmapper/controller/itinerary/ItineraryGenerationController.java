package com.travelmapper.controller.itinerary;

import com.travelmapper.common.api.ApiResponse;
import com.travelmapper.common.security.CurrentUser;
import com.travelmapper.controller.itinerary.dto.GenerateItineraryRequest;
import com.travelmapper.controller.itinerary.dto.GenerateItineraryResponse;
import com.travelmapper.service.itinerary.ItineraryGenerationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Itinerary Generation")
@RestController
@RequestMapping("/api/itinerary")
public class ItineraryGenerationController {

    private final ItineraryGenerationService generationService;

    public ItineraryGenerationController(ItineraryGenerationService generationService) {
        this.generationService = generationService;
    }

    @PostMapping("/generate")
    public ApiResponse<GenerateItineraryResponse> generate(
            @Valid @RequestBody GenerateItineraryRequest request,
            HttpServletResponse response) {
        GenerateItineraryResponse result = generationService.generate(CurrentUser.id(), request);
        if (result.deduplicated()) {
            response.setHeader("X-Dedup", "hit");
        }
        return ApiResponse.ok(result);
    }
}
