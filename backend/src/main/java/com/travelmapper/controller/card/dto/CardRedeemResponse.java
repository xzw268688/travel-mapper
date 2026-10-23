package com.travelmapper.controller.card.dto;

import java.time.LocalDateTime;

public record CardRedeemResponse(
        String cardType,
        int value,
        LocalDateTime vipExpireAtAfter,
        int packBalanceAfter,
        boolean idempotentReplay) {
}
