package com.travelmapper.controller.admin;

import com.travelmapper.common.api.ApiResponse;
import com.travelmapper.controller.prep.dto.OfficialLinkDtos;
import com.travelmapper.service.prep.OfficialLinkService;
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

import java.util.List;

@Tag(name = "Admin Official Links")
@RestController
@RequestMapping("/api/admin/official-links")
public class AdminOfficialLinkController {

    private final OfficialLinkService officialLinkService;

    public AdminOfficialLinkController(OfficialLinkService officialLinkService) {
        this.officialLinkService = officialLinkService;
    }

    @GetMapping
    public ApiResponse<List<OfficialLinkDtos.AdminView>> list(
            @RequestParam(required = false) String countryCode) {
        return ApiResponse.ok(officialLinkService.adminList(countryCode));
    }

    @PostMapping
    public ApiResponse<OfficialLinkDtos.AdminView> create(
            @Valid @RequestBody OfficialLinkDtos.SaveRequest request) {
        return ApiResponse.ok(officialLinkService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<OfficialLinkDtos.AdminView> update(
            @PathVariable Long id, @Valid @RequestBody OfficialLinkDtos.SaveRequest request) {
        return ApiResponse.ok(officialLinkService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        officialLinkService.delete(id);
        return ApiResponse.ok();
    }
}
