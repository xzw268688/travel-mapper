package com.travelmapper.controller.guide.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class CountryGuideRequest {

    @NotBlank
    @Pattern(regexp = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
    private String requestId;

    @NotBlank
    @Pattern(regexp = "[A-Za-z]{2}")
    private String countryCode;
}
