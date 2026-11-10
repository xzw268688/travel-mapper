package com.travelmapper.service.search;

public class SearchTimeoutException extends RuntimeException {

    public SearchTimeoutException(String message) {
        super(message);
    }

    public SearchTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
