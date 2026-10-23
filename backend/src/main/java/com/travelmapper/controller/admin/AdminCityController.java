package com.travelmapper.controller.admin;

import com.travelmapper.common.api.ApiResponse;
import com.travelmapper.common.api.PageRequest;
import com.travelmapper.common.api.PageResult;
import com.travelmapper.controller.admin.dto.AdminCityView;
import com.travelmapper.controller.admin.dto.CreateCityRequest;
import com.travelmapper.controller.admin.dto.DeleteCityResponse;
import com.travelmapper.controller.admin.dto.UpdateCityRequest;
import com.travelmapper.repository.enums.EnabledStatus;
import com.travelmapper.service.catalog.CatalogService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin Cities")
@RestController
@RequestMapping("/api/admin/cities")
public class AdminCityController {

    private final CatalogService catalogService;

    public AdminCityController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping
    public ApiResponse<PageResult<AdminCityView>> list(
            @RequestParam(required = false) String countryCode,
            @RequestParam(required = false) EnabledStatus status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest request = new PageRequest();
        request.setPage(page);
        request.setSize(size);
        return ApiResponse.ok(catalogService.adminList(countryCode, status, keyword, request));
    }

    @PostMapping
    public ApiResponse<AdminCityView> create(@Valid @RequestBody CreateCityRequest request) {
        return ApiResponse.ok(catalogService.createCity(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<AdminCityView> update(@PathVariable Long id,
                                             @Valid @RequestBody UpdateCityRequest request) {
        return ApiResponse.ok(catalogService.updateCity(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<DeleteCityResponse> delete(@PathVariable Long id) {
        return ApiResponse.ok(catalogService.deleteCity(id));
    }
}
