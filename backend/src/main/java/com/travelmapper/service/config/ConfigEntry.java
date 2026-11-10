package com.travelmapper.service.config;

public record ConfigEntry(String value, boolean encrypted, boolean masked, String remark) {

    public static ConfigEntry plain(String value, String remark) {
        return new ConfigEntry(value, false, false, remark);
    }

    public static ConfigEntry secret(String value, String remark) {
        return new ConfigEntry(value, true, true, remark);
    }
}
