package com.travelmapper.controller.user.dto;

import java.time.LocalDateTime;

public record DeleteAccountResponse(LocalDateTime deletedAt) {
}
