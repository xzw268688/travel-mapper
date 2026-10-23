package com.travelmapper.controller.auth.dto;

public record LoginResponse(String token, long expiresIn, UserView user) {
}
