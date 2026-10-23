package com.travelmapper.controller.admin.dto;

import com.travelmapper.repository.enums.EnabledStatus;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateCityRequest {

    @Size(min = 1, max = 64)
    private String name;

    private EnabledStatus status;
}
