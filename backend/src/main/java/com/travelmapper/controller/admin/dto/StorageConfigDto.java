package com.travelmapper.controller.admin.dto;

import lombok.Data;

@Data
public class StorageConfigDto {
    private String provider;
    private String endpoint;
    private String region;
    private String bucket;
    private String accessKey;
    private String secretKey;
    private Boolean pathStyle;
    private String publicDomain;
}
