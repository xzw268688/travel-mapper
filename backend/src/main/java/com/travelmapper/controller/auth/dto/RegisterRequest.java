package com.travelmapper.controller.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {

    @NotBlank
    @Pattern(regexp = "[A-Za-z0-9_]{4,20}", message = "用户名必须为 4-20 位字母、数字或下划线")
    private String username;

    @NotBlank
    @Email
    @Size(max = 128)
    private String email;

    @NotBlank
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,32}$",
            message = "密码必须为 8-32 位且同时包含字母与数字")
    private String password;

    @Size(max = 20)
    private String nickname;

    @NotNull
    private Boolean agreeTerms;

    @NotBlank
    @Pattern(regexp = "\\d{6}", message = "验证码必须为 6 位数字")
    private String code;
}
