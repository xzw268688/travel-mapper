package com.travelmapper.controller.user.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateProfileRequest {

    @Size(min = 1, max = 20)
    private String nickname;

    @Pattern(regexp = "preset_(0[1-9]|1[0-2])", message = "头像必须是 preset_01～preset_12")
    private String avatarPreset;
}
