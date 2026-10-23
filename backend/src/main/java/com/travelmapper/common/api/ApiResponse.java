package com.travelmapper.common.api;

import org.slf4j.MDC;

public record ApiResponse<T>(int code, String message, T data, String traceId) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(ErrorCode.OK.getCode(), ErrorCode.OK.getMessage(), data, currentTraceId());
    }

    public static ApiResponse<Void> ok() {
        return ok(null);
    }

    public static <T> ApiResponse<T> error(ErrorCode errorCode) {
        return error(errorCode, errorCode.getMessage());
    }

    public static <T> ApiResponse<T> error(ErrorCode errorCode, String message) {
        return new ApiResponse<>(errorCode.getCode(), message, null, currentTraceId());
    }

    public static <T> ApiResponse<T> error(int code, String message) {
        return new ApiResponse<>(code, message, null, currentTraceId());
    }

    private static String currentTraceId() {
        return MDC.get("traceId");
    }
}
