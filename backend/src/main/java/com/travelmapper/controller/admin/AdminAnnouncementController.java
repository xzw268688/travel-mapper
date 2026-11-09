package com.travelmapper.controller.admin;

import com.travelmapper.common.api.ApiResponse;
import com.travelmapper.common.api.PageRequest;
import com.travelmapper.common.api.PageResult;
import com.travelmapper.common.security.CurrentUser;
import com.travelmapper.controller.admin.dto.AdminAnnouncementDetailView;
import com.travelmapper.controller.admin.dto.AdminAnnouncementView;
import com.travelmapper.controller.admin.dto.AnnouncementRequest;
import com.travelmapper.service.announcement.AnnouncementService;
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

@Tag(name = "Admin Announcements")
@RestController
@RequestMapping("/api/admin/announcements")
public class AdminAnnouncementController {

    private final AnnouncementService announcementService;

    public AdminAnnouncementController(AnnouncementService announcementService) {
        this.announcementService = announcementService;
    }

    @GetMapping
    public ApiResponse<PageResult<AdminAnnouncementView>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest request = new PageRequest();
        request.setPage(page);
        request.setSize(size);
        return ApiResponse.ok(announcementService.adminList(status, type, keyword, request));
    }

    @GetMapping("/{id}")
    public ApiResponse<AdminAnnouncementDetailView> detail(@PathVariable Long id) {
        return ApiResponse.ok(announcementService.adminDetail(id));
    }

    @PostMapping
    public ApiResponse<AdminAnnouncementDetailView> create(
            @Valid @RequestBody AnnouncementRequest request) {
        return ApiResponse.ok(announcementService.create(CurrentUser.id(), request));
    }

    @PutMapping("/{id}")
    public ApiResponse<AdminAnnouncementDetailView> update(
            @PathVariable Long id, @Valid @RequestBody AnnouncementRequest request) {
        return ApiResponse.ok(announcementService.update(CurrentUser.id(), id, request));
    }

    @PutMapping("/{id}/publish")
    public ApiResponse<AdminAnnouncementDetailView> publish(@PathVariable Long id) {
        return ApiResponse.ok(announcementService.publish(CurrentUser.id(), id));
    }

    @PutMapping("/{id}/offline")
    public ApiResponse<AdminAnnouncementDetailView> offline(@PathVariable Long id) {
        return ApiResponse.ok(announcementService.offline(CurrentUser.id(), id));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        announcementService.delete(CurrentUser.id(), id);
        return ApiResponse.ok();
    }
}