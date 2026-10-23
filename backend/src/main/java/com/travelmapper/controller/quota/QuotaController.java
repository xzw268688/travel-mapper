package com.travelmapper.controller.quota;

import com.travelmapper.common.api.ApiResponse;
import com.travelmapper.common.security.CurrentUser;
import com.travelmapper.service.quota.QuotaService;
import com.travelmapper.service.quota.QuotaSummary;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Quota")
@RestController
@RequestMapping("/api/quota")
public class QuotaController {

    private final QuotaService quotaService;

    public QuotaController(QuotaService quotaService) {
        this.quotaService = quotaService;
    }

    @GetMapping("/me")
    public ApiResponse<QuotaSummary> me() {
        return ApiResponse.ok(quotaService.getSummary(CurrentUser.id()));
    }
}
