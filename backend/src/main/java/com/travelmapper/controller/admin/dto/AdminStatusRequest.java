package com.travelmapper.controller.admin.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AdminStatusRequest {

    @NotBlank
    private String status;

    private String reason;
}
