package com.travelmapper.controller.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ChangeEmailRequest {

    @NotBlank
    @Email
    @Size(max = 128)
    private String newEmail;

    @NotBlank
    private String password;

    @NotBlank
    @Pattern(regexp = "\\d{6}", message = "验证码必须为 6 位数字")
    private String code;
}