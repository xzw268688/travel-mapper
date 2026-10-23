package com.travelmapper.common.security;

public record UserAuthSnapshot(Long userId, String role, String status, int tokenVersion) {
}
