package com.travelmapper.controller.admin;

import com.travelmapper.common.api.ApiResponse;
import com.travelmapper.common.security.CurrentUser;
import com.travelmapper.controller.note.dto.NoteDtos;
import com.travelmapper.service.note.NoteService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin Notes")
@RestController
@RequestMapping("/api/admin/notes")
public class AdminNoteController {

    private final NoteService noteService;

    public AdminNoteController(NoteService noteService) {
        this.noteService = noteService;
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
}
