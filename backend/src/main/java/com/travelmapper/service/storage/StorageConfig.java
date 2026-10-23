package com.travelmapper.service.storage;

public record StorageConfig(
        String provider,
        String endpoint,
        String region,
        String bucket,
        String accessKey,
        String secretKey,
        boolean pathStyle,
        String publicDomain) {

    public boolean isConfigured() {
        return endpoint != null && !endpoint.isBlank()
                && bucket != null && !bucket.isBlank()
                && accessKey != null && !accessKey.isBlank()
                && secretKey != null && !secretKey.isBlank();
    }
}
