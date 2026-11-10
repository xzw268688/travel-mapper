package com.travelmapper.controller.admin.dto;

import com.travelmapper.repository.enums.CityLevel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateCityRequest {

    @NotBlank
    @Pattern(regexp = "[A-Za-z0-9]{2}", message = "国家代码必须为 2 位字母或数字")
    private String countryCode;

    @NotBlank
    @Pattern(regexp = "[A-Z0-9]{2,8}", message = "城市代码必须为 2-8 位大写字母或数字")
    private String cityCode;

    @NotBlank
    @Size(max = 64)
    private String name;

    private CityLevel level;
}
