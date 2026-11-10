package com.travelmapper.controller.admin;

import com.travelmapper.common.api.ApiResponse;
import com.travelmapper.common.api.PageRequest;
import com.travelmapper.common.api.PageResult;
import com.travelmapper.controller.admin.dto.AdminLoginLogView;
import com.travelmapper.service.admin.AdminLoginLogService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@Tag(name = "Admin Logs")
@RestController
@RequestMapping("/api/admin/login-logs")
public class AdminLoginLogController {

    private final AdminLoginLogService loginLogService;

    public AdminLoginLogController(AdminLoginLogService loginLogService) {
        this.loginLogService = loginLogService;
    }

    @GetMapping
    public ApiResponse<PageResult<AdminLoginLogView>> list(
            @RequestParam(required = false) String username,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) Boolean success,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest request = new PageRequest();
        request.setPage(page);
        request.setSize(size);
        return ApiResponse.ok(loginLogService.list(username, userId, role, success, from, to, request));
    }
}