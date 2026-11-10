package com.travelmapper.controller.trip.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TripStatusRequest {

    /** PLANNED 或 COMPLETED。 */
    @NotBlank
    private String status;
}
