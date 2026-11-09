package com.travelmapper.controller.admin.dto;

import com.travelmapper.repository.enums.AnnouncementType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AnnouncementRequest {

    @NotBlank
    @Size(max = 100)
    private String title;

    @NotBlank
    @Size(max = 5000)
    private String content;

    @NotNull
    private AnnouncementType type;

    private Boolean pinned;

    private LocalDateTime publishAt;

    private LocalDateTime expireAt;
}