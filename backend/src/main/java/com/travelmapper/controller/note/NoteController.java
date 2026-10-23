package com.travelmapper.controller.note;

import com.travelmapper.common.api.ApiResponse;
import com.travelmapper.common.security.CurrentUser;
import com.travelmapper.controller.note.dto.NoteDtos;
import com.travelmapper.controller.note.dto.ShareDtos;
import com.travelmapper.service.note.NoteService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Notes")
@RestController
@RequestMapping("/api/notes")
public class NoteController {

    private final NoteService noteService;

    public NoteController(NoteService noteService) {
        this.noteService = noteService;
    }

    @PostMapping
    public ApiResponse<NoteDtos.CreateResponse> create(
            @Valid @RequestBody NoteDtos.CreateRequest request) {
        return ApiResponse.ok(noteService.create(CurrentUser.id(), request));
    }

    @GetMapping("/{id}")
    public ApiResponse<NoteDtos.Response> detail(@PathVariable Long id) {
        return ApiResponse.ok(noteService.detail(CurrentUser.id(), id));
    }

    @PutMapping("/{id}")
    public ApiResponse<NoteDtos.Response> update(
            @PathVariable Long id, @Valid @RequestBody NoteDtos.UpdateRequest request) {
        return ApiResponse.ok(noteService.update(CurrentUser.id(), id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<NoteDtos.DeleteResponse> delete(@PathVariable Long id) {
        return ApiResponse.ok(noteService.delete(CurrentUser.id(), id));
    }

    @PostMapping("/{id}/share")
    public ApiResponse<ShareDtos.ShareResponse> share(
            @PathVariable Long id, @RequestBody(required = false) ShareDtos.ShareRequest request) {
        Integer expireDays = request == null ? null : request.getExpireDays();
        return ApiResponse.ok(noteService.share(CurrentUser.id(), id, expireDays));
    }

    @DeleteMapping("/{id}/share")
    public ApiResponse<ShareDtos.RevokeResponse> revokeShare(@PathVariable Long id) {
        return ApiResponse.ok(noteService.revokeShare(CurrentUser.id(), id));
    }
}
