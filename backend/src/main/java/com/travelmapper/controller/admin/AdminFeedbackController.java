package com.travelmapper.controller.admin;

import com.travelmapper.common.api.ApiResponse;
import com.travelmapper.common.api.PageRequest;
import com.travelmapper.common.api.PageResult;
import com.travelmapper.common.security.CurrentUser;
import com.travelmapper.controller.admin.dto.AdminFeedbackDetailView;
import com.travelmapper.controller.admin.dto.AdminFeedbackView;
import com.travelmapper.controller.admin.dto.FeedbackStatsView;
import com.travelmapper.controller.admin.dto.UpdateFeedbackRequest;
import com.travelmapper.service.feedback.FeedbackService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@Tag(name = "Admin Feedback")
@RestController
@RequestMapping("/api/admin/feedback")
public class AdminFeedbackController {

    private final FeedbackService feedbackService;

    public AdminFeedbackController(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    @GetMapping
    public ApiResponse<PageResult<AdminFeedbackView>> list(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest request = new PageRequest();
        request.setPage(page);
        request.setSize(size);
        return ApiResponse.ok(feedbackService.adminList(type, status, keyword, from, to, request));
    }

    @GetMapping("/stats")
    public ApiResponse<FeedbackStatsView> stats() {
        return ApiResponse.ok(feedbackService.stats());
    }

    @GetMapping("/{id}")
    public ApiResponse<AdminFeedbackDetailView> detail(@PathVariable Long id) {
        return ApiResponse.ok(feedbackService.detail(id));
    }

    @PutMapping("/{id}")
    public ApiResponse<AdminFeedbackDetailView> update(
            @PathVariable Long id, @Valid @RequestBody UpdateFeedbackRequest request) {
        return ApiResponse.ok(feedbackService.update(CurrentUser.id(), id, request));
    }
}