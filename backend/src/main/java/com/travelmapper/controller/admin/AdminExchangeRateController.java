package com.travelmapper.controller.admin;

import com.travelmapper.common.api.ApiResponse;
import com.travelmapper.controller.admin.dto.ExchangeRateDtos;
import com.travelmapper.service.expense.ExchangeRateService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Admin Exchange Rates")
@RestController
@RequestMapping("/api/admin/exchange-rates")
public class AdminExchangeRateController {

    private final ExchangeRateService exchangeRateService;

    public AdminExchangeRateController(ExchangeRateService exchangeRateService) {
        this.exchangeRateService = exchangeRateService;
    }

    @GetMapping
    public ApiResponse<List<ExchangeRateDtos.RateView>> list() {
        return ApiResponse.ok(exchangeRateService.list());
    }

    @PutMapping
    public ApiResponse<ExchangeRateDtos.UpdateResponse> upsert(
            @Valid @RequestBody ExchangeRateDtos.UpdateRequest request) {
        return ApiResponse.ok(exchangeRateService.upsert(request));
    }
}
