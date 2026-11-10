package com.travelmapper.controller.trip.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class TripRenameRequest {

    @NotBlank
    @Size(max = 30)
    private String name;
}
