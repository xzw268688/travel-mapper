package com.travelmapper.controller.admin.dto;

import lombok.Data;

@Data
public class AlertConfigDto {

    private Boolean enabled;
    private String webhookUrl;
    private Integer minIntervalSeconds;
}