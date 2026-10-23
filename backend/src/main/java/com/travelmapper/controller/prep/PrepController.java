package com.travelmapper.controller.prep;

import com.travelmapper.common.api.ApiResponse;
import com.travelmapper.common.security.CurrentUser;
import com.travelmapper.controller.prep.dto.ChecklistDtos;
import com.travelmapper.controller.prep.dto.OfficialLinkDtos;
import com.travelmapper.service.prep.ChecklistService;
import com.travelmapper.service.prep.OfficialLinkService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Preparations")
@RestController
@RequestMapping("/api/prep")
public class PrepController {

    private final ChecklistService checklistService;
    private final OfficialLinkService officialLinkService;

    public PrepController(ChecklistService checklistService, OfficialLinkService officialLinkService) {
        this.checklistService = checklistService;
        this.officialLinkService = officialLinkService;
    }

    @GetMapping("/checklist")
    public ApiResponse<ChecklistDtos.ChecklistResponse> checklist(@RequestParam Long itineraryId) {
        return ApiResponse.ok(checklistService.get(CurrentUser.id(), itineraryId));
    }

    @PutMapping("/checklist")
    public ApiResponse<ChecklistDtos.UpdateResponse> updateChecklist(
            @Valid @RequestBody ChecklistDtos.UpdateRequest request) {
        return ApiResponse.ok(checklistService.upsert(CurrentUser.id(), request));
    }

    @GetMapping("/official-links")
    public ApiResponse<OfficialLinkDtos.PublicResponse> officialLinks(
            @RequestParam String countryCode) {
        return ApiResponse.ok(officialLinkService.getPublic(countryCode));
    }
}
