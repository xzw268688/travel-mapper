package com.travelmapper.controller.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class ResetPasswordRequest {

    @NotBlank
    private String token;

    @NotBlank
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,32}$",
            message = "密码必须为 8-32 位且同时包含字母与数字")
    private String newPassword;
}
