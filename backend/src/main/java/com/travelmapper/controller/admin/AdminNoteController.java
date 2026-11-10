package com.travelmapper.controller.admin;

import com.travelmapper.common.api.ApiResponse;
import com.travelmapper.common.api.PageRequest;
import com.travelmapper.common.api.PageResult;
import com.travelmapper.common.security.CurrentUser;
import com.travelmapper.controller.admin.dto.AdminNoteDetailView;
import com.travelmapper.controller.admin.dto.AdminNoteView;
import com.travelmapper.controller.note.dto.NoteDtos;
import com.travelmapper.service.note.NoteService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@Tag(name = "Admin Notes")
@RestController
@RequestMapping("/api/admin/notes")
public class AdminNoteController {

    private final NoteService noteService;

    public AdminNoteController(NoteService noteService) {
        this.noteService = noteService;
    }

    @GetMapping
    public ApiResponse<PageResult<AdminNoteView>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String mediaReview,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest request = new PageRequest();
        request.setPage(page);
        request.setSize(size);
        return ApiResponse.ok(noteService.adminList(
                keyword, status, mediaReview, from, to, request));
    }

    @GetMapping("/{id}")
    public ApiResponse<AdminNoteDetailView> detail(@PathVariable Long id) {
        return ApiResponse.ok(noteService.adminDetail(id));
    }

    @PutMapping("/{id}/takedown")
    public ApiResponse<NoteDtos.TakedownResponse> takedown(
            @PathVariable Long id, @RequestBody(required = false) NoteDtos.TakedownRequest request) {
        String reason = request == null ? null : request.getReason();
        return ApiResponse.ok(noteService.adminTakedown(CurrentUser.id(), id, reason));
    }

    @PutMapping("/{id}/restore")
    public ApiResponse<NoteDtos.RestoreResponse> restore(@PathVariable Long id) {
        return ApiResponse.ok(noteService.adminRestore(CurrentUser.id(), id));
    }

    @PutMapping("/{id}/media-review")
    public ApiResponse<NoteDtos.MediaReviewResponse> mediaReview(@PathVariable Long id) {
        return ApiResponse.ok(noteService.adminMarkMediaReviewed(CurrentUser.id(), id));
    }
}
