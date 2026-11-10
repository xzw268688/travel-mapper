package com.travelmapper.controller.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SendCodeRequest {

    @NotBlank
    @Email
    private String email;

    /** VERIFY（注册验证）或 RESET（找回密码）。 */
    @NotBlank
    private String scene;
}