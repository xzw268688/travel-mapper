package com.travelmapper.controller.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AcceptReportRequest {

    @NotBlank
    @Size(max = 100)
    private String noteReason;
}