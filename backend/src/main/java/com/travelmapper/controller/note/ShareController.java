package com.travelmapper.controller.note;

import com.travelmapper.common.api.ApiResponse;
import com.travelmapper.common.security.RequestContext;
import com.travelmapper.controller.note.dto.ReportDtos;
import com.travelmapper.controller.note.dto.ShareDtos;
import com.travelmapper.service.note.NoteService;
import com.travelmapper.service.report.ReportService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Public Share")
@RestController
@RequestMapping("/s")
public class ShareController {

    private final NoteService noteService;
    private final ReportService reportService;

    public ShareController(NoteService noteService, ReportService reportService) {
        this.noteService = noteService;
        this.reportService = reportService;
    }

    @GetMapping("/{token}")
    public ApiResponse<ShareDtos.SharePageResponse> page(@PathVariable String token) {
        return ApiResponse.ok(noteService.publicPage(token));
    }

    @PostMapping("/{token}/reports")
    public ApiResponse<ReportDtos.Receipt> report(
            @PathVariable String token,
            @Valid @RequestBody ReportDtos.CreateRequest request,
            HttpServletRequest servletRequest) {
        return ApiResponse.ok(reportService.submit(
                token, clientIp(servletRequest), RequestContext.userIdOrNull(), request));
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String first = forwarded.split(",")[0].trim();
            if (!first.isEmpty()) {
                return first;
            }
        }
        return request.getRemoteAddr();
    }
}
