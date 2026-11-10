package com.travelmapper.controller.catalog;

import com.travelmapper.common.api.ApiResponse;
import com.travelmapper.controller.catalog.dto.CountryView;
import com.travelmapper.service.catalog.CatalogService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Catalog")
@RestController
@RequestMapping("/api/countries")
public class CountryController {

    private final CatalogService catalogService;

    public CountryController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping
    public ApiResponse<List<CountryView>> list() {
        return ApiResponse.ok(catalogService.listCountries());
    }
}
