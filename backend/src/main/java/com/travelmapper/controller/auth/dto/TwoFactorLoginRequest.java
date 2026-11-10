package com.travelmapper.controller.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class TwoFactorLoginRequest {

    @NotBlank
    private String challengeToken;

    /** 认证器 6 位动态码或一次性恢复码。 */
    @NotBlank
    @Size(max = 20)
    private String code;
}