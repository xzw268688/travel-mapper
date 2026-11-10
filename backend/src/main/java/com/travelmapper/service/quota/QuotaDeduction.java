package com.travelmapper.service.quota;

public record QuotaDeduction(String source, int freeUsedToday, int freeLimit, int packBalance) {
}
