package com.travelmapper.controller.announcement;

import com.travelmapper.common.api.ApiResponse;
import com.travelmapper.common.security.CurrentUser;
import com.travelmapper.controller.announcement.dto.AnnouncementUserDtos;
import com.travelmapper.service.announcement.AnnouncementService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Announcements")
@RestController
@RequestMapping("/api/announcements")
public class AnnouncementController {

    private final AnnouncementService announcementService;

    public AnnouncementController(AnnouncementService announcementService) {
        this.announcementService = announcementService;
    }

    @GetMapping("/active")
    public ApiResponse<AnnouncementUserDtos.ActiveAnnouncementsResponse> active() {
        return ApiResponse.ok(announcementService.activeForUser(CurrentUser.id()));
    }

    @PostMapping("/{id}/read")
    public ApiResponse<Void> markRead(@PathVariable Long id) {
        announcementService.markRead(CurrentUser.id(), id);
        return ApiResponse.ok();
    }
}