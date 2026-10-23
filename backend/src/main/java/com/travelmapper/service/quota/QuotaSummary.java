package com.travelmapper.service.quota;

import java.time.LocalDateTime;

public record QuotaSummary(
        String plan,
        LocalDateTime vipExpireAt,
        int freeUsedToday,
        int freeLimit,
        int packBalance,
        PackExpiringSoon packExpiringSoon,
        int vipDailyLimit,
        int vipDailyUsed,
        LocalDateTime resetAt) {

    public record PackExpiringSoon(int count, LocalDateTime expireAt) {
    }
}
