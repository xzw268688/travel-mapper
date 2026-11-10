package com.travelmapper.controller.admin.dto;

import lombok.Data;

@Data
public class SearchConfigDto {
    private String provider;
    private String apiKey;
    private Integer dailyLimit;
    private Integer timeoutSeconds;
    private Integer cacheHours;
    private String domainWhitelist;
}
