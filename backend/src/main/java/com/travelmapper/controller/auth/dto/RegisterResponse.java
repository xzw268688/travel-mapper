package com.travelmapper.controller.auth.dto;

import java.time.LocalDateTime;

public record RegisterResponse(Long userId, boolean emailSent, LocalDateTime verifyExpireAt) {
}
