package com.travelmapper.controller.user.dto;

public record TokenResponse(String token, long expiresIn) {
}