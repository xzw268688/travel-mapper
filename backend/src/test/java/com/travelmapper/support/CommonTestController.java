package com.travelmapper.support;

import com.travelmapper.common.api.ApiResponse;
import com.travelmapper.common.api.BusinessException;
import com.travelmapper.common.api.ErrorCode;
import jakarta.validation.constraints.Min;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Validated
@RestController
@RequestMapping("/test")
public class CommonTestController {

    public static final AtomicInteger IDEMPOTENT_COUNTER = new AtomicInteger();

    private final JdbcTemplate jdbcTemplate;

    public CommonTestController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/errors/business")
    public ApiResponse<Void> businessError() {
        throw new BusinessException(ErrorCode.ITINERARY_NOT_FOUND);
    }

    @GetMapping("/errors/boom")
    public ApiResponse<Void> boom() {
        throw new IllegalStateException("boom");
    }

    @GetMapping("/errors/validation")
    public ApiResponse<Void> validation(@RequestParam @Min(1) int n) {
        return ApiResponse.ok();
    }

    @GetMapping("/errors/duplicate")
    public ApiResponse<Void> duplicate() {
        jdbcTemplate.update("INSERT INTO country (code, name_zh, status, sort_order) "
                + "VALUES ('JP', 'duplicate', 'ENABLED', 99)");
        return ApiResponse.ok();
    }

    @PostMapping("/idempotency")
    public ApiResponse<Map<String, Object>> idempotency() {
        return ApiResponse.ok(Map.of("count", IDEMPOTENT_COUNTER.incrementAndGet()));
    }
}
