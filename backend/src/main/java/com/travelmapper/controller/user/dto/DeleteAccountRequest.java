package com.travelmapper.controller.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DeleteAccountRequest {

    @NotBlank
    private String password;

    @NotBlank
    private String confirmText;
}
