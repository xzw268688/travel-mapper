package com.travelmapper.controller.catalog;

import com.travelmapper.common.api.ApiResponse;
import com.travelmapper.controller.catalog.dto.CityView;
import com.travelmapper.service.catalog.CatalogService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Catalog")
@RestController
@RequestMapping("/api/cities")
public class CityController {

    private final CatalogService catalogService;

    public CityController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping
    public ApiResponse<List<CityView>> list(@RequestParam String countryCode) {
        return ApiResponse.ok(catalogService.listCities(countryCode));
    }
}
