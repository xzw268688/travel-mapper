package com.travelmapper.controller.auth.dto;

import com.travelmapper.common.security.JwtService;

public record LoginResponse(String token, long expiresIn, UserView user,
                            boolean requires2fa, String challengeToken) {

    public static LoginResponse authenticated(String token, long expiresIn, UserView user) {
        return new LoginResponse(token, expiresIn, user, false, null);
    }

    public static LoginResponse twoFactorChallenge(String challengeToken) {
        return new LoginResponse(null, JwtService.TWO_FACTOR_CHALLENGE_TTL_SECONDS, null,
                true, challengeToken);
    }
}