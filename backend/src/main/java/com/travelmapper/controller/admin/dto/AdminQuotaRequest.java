package com.travelmapper.controller.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AdminQuotaRequest {

    @NotBlank
    private String type;

    @NotNull
    private Integer delta;

    @NotBlank
    private String reason;
}
