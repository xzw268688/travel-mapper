package com.travelmapper.controller.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class TwoFactorCredentialRequest {

    @NotBlank
    private String password;

    @NotBlank
    @Pattern(regexp = "\\d{6}", message = "验证码必须为 6 位数字")
    private String code;
}