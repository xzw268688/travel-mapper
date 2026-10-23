package com.travelmapper.common.api;

import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final String userMessage;

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, errorCode.getMessage());
    }

    public BusinessException(ErrorCode errorCode, String userMessage) {
        super(userMessage);
        this.errorCode = errorCode;
        this.userMessage = userMessage;
    }
}
