package com.travelmapper.controller.admin.dto;

import java.time.LocalDateTime;

public record AdminVipResponse(Long userId, LocalDateTime vipExpireAt) {
}
