package com.travelmapper.service.quota;

public enum AiFailureReason {
    TIMEOUT,
    MODEL_ERROR,
    JSON_FAILURE,
    NO_OUTPUT_DISCONNECT,
    USER_CANCELLED,
    PARTIAL_OUTPUT
}
