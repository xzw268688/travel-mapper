package com.travelmapper.controller.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ResendVerifyRequest {

    @NotBlank
    @Email
    private String email;
}
