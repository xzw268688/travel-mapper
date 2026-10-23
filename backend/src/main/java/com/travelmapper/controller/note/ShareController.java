package com.travelmapper.controller.note;

import com.travelmapper.common.api.ApiResponse;
import com.travelmapper.controller.note.dto.ShareDtos;
import com.travelmapper.service.note.NoteService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Public Share")
@RestController
@RequestMapping("/s")
public class ShareController {

    private final NoteService noteService;

    public ShareController(NoteService noteService) {
        this.noteService = noteService;
    }

    @GetMapping("/{token}")
    public ApiResponse<ShareDtos.SharePageResponse> page(@PathVariable String token) {
        return ApiResponse.ok(noteService.publicPage(token));
    }
}
