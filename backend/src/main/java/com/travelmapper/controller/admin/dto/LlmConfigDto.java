package com.travelmapper.controller.admin.dto;

import lombok.Data;

@Data
public class LlmConfigDto {
    private String provider;
    private String baseUrl;
    private String model;
    private String apiKey;
    private Integer timeoutSeconds;
    private Integer maxRetry;
}
