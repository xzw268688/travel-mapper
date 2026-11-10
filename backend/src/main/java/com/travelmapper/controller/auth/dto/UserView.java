package com.travelmapper.controller.auth.dto;

import java.time.LocalDateTime;

public record UserView(
        Long id,
        String username,
        String nickname,
        String avatarPreset,
        String role,
        String plan,
        LocalDateTime vipExpireAt) {
}
