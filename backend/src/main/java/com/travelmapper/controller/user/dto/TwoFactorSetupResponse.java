package com.travelmapper.controller.user.dto;

public record TwoFactorSetupResponse(String secret, String otpauthUri) {
}