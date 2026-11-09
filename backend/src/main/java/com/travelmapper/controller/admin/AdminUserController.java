package com.travelmapper.controller.admin;

import com.travelmapper.common.api.ApiResponse;
import com.travelmapper.common.api.PageRequest;
import com.travelmapper.common.api.PageResult;
import com.travelmapper.common.security.CurrentUser;
import com.travelmapper.controller.admin.dto.AdminQuotaRequest;
import com.travelmapper.controller.admin.dto.AdminQuotaResponse;
import com.travelmapper.controller.admin.dto.AdminStatusRequest;
import com.travelmapper.controller.admin.dto.AdminStatusResponse;
import com.travelmapper.controller.admin.dto.AdminUserDetailView;
import com.travelmapper.controller.admin.dto.AdminUserView;
import com.travelmapper.controller.admin.dto.AdminVipRequest;
import com.travelmapper.controller.admin.dto.AdminVipResponse;
import com.travelmapper.service.admin.AdminUserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin Users")
@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @GetMapping
    public ApiResponse<PageResult<AdminUserView>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest request = new PageRequest();
        request.setPage(page);
        request.setSize(size);
        return ApiResponse.ok(adminUserService.list(keyword, status, request));
    }

    @GetMapping("/{id}")
    public ApiResponse<AdminUserDetailView> detail(@PathVariable Long id) {
        return ApiResponse.ok(adminUserService.detail(id));
    }

    @PutMapping("/{id}/vip")
    public ApiResponse<AdminVipResponse> setVip(
            @PathVariable Long id, @RequestBody AdminVipRequest request) {
        return ApiResponse.ok(adminUserService.setVip(CurrentUser.id(), id, request));
    }

    @PutMapping("/{id}/status")
    public ApiResponse<AdminStatusResponse> updateStatus(
            @PathVariable Long id, @Valid @RequestBody AdminStatusRequest request) {
        return ApiResponse.ok(adminUserService.updateStatus(CurrentUser.id(), id, request));
    }

    @PutMapping("/{id}/quota")
    public ApiResponse<AdminQuotaResponse> adjustQuota(
            @PathVariable Long id, @Valid @RequestBody AdminQuotaRequest request) {
        return ApiResponse.ok(adminUserService.adjustQuota(CurrentUser.id(), id, request));
    }
}
