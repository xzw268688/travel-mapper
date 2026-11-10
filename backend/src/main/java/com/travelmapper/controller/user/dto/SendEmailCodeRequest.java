package com.travelmapper.controller.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SendEmailCodeRequest {

    @NotBlank
    @Email
    @Size(max = 128)
    private String email;
}