package com.travelmapper.repository.enums;

/** 旅行对外展示的有效状态；IN_PROGRESS 由日期推导，不落库。 */
public enum EffectiveTripStatus {
    PLANNED,
    IN_PROGRESS,
    COMPLETED
}