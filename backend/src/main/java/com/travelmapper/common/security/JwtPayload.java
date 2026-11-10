package com.travelmapper.common.security;

public record JwtPayload(Long userId, String role, int tokenVersion, String jti,
                         long expiresAtEpochSecond, String purpose) {
}
