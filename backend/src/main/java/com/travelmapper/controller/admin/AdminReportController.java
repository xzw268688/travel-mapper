package com.travelmapper.controller.admin;

import com.travelmapper.common.api.ApiResponse;
import com.travelmapper.common.api.PageRequest;
import com.travelmapper.common.api.PageResult;
import com.travelmapper.common.security.CurrentUser;
import com.travelmapper.controller.admin.dto.AcceptReportRequest;
import com.travelmapper.controller.admin.dto.AdminReportDetailView;
import com.travelmapper.controller.admin.dto.AdminReportView;
import com.travelmapper.controller.admin.dto.ReportStatsView;
import com.travelmapper.service.report.ReportService;
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

@Tag(name = "Admin Reports")
@RestController
@RequestMapping("/api/admin/reports")
public class AdminReportController {

    private final ReportService reportService;

    public AdminReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping
    public ApiResponse<PageResult<AdminReportView>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String reason,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest request = new PageRequest();
        request.setPage(page);
        request.setSize(size);
        return ApiResponse.ok(reportService.list(status, reason, keyword, from, to, request));
    }

    @GetMapping("/stats")
    public ApiResponse<ReportStatsView> stats() {
        return ApiResponse.ok(reportService.stats());
    }

    @GetMapping("/{id}")
    public ApiResponse<AdminReportDetailView> detail(@PathVariable Long id) {
        return ApiResponse.ok(reportService.detail(id));
    }

    @PutMapping("/{id}/accept")
    public ApiResponse<AdminReportDetailView> accept(
            @PathVariable Long id, @Valid @RequestBody AcceptReportRequest request) {
        return ApiResponse.ok(reportService.accept(CurrentUser.id(), id, request));
    }

    @PutMapping("/{id}/reject")
    public ApiResponse<AdminReportDetailView> reject(@PathVariable Long id) {
        return ApiResponse.ok(reportService.reject(CurrentUser.id(), id));
    }
}