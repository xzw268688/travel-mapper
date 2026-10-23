package com.travelmapper.controller.itinerary.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class GenerateItineraryRequest {

    @NotBlank
    @Pattern(regexp = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}",
            message = "requestId 必须为 UUID v4")
    private String requestId;

    @NotBlank
    @Pattern(regexp = "[A-Za-z]{2}")
    private String countryCode;

    @NotEmpty
    @Size(min = 1, max = 5)
    private List<@Pattern(regexp = "[A-Za-z0-9]{2,8}") String> cityCodes;

    @Min(1)
    @Max(15)
    private int days;

    @NotNull
    private LocalDate startDate;

    @Size(max = 200)
    private String preference;
}
