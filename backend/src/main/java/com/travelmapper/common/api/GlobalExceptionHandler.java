package com.travelmapper.common.api;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException ex) {
        ErrorCode code = ex.getErrorCode();
        if (code.getHttpStatus() >= 500) {
            log.error("business failure code={} message={}", code.getCode(), ex.getUserMessage(), ex);
        } else {
            log.warn("business failure code={} message={}", code.getCode(), ex.getUserMessage());
        }
        return ResponseEntity.status(code.getHttpStatus()).body(ApiResponse.error(code, ex.getUserMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .orElse(ErrorCode.PARAM_INVALID.getMessage());
        return ResponseEntity.badRequest().body(ApiResponse.error(ErrorCode.PARAM_INVALID, message));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraint(ConstraintViolationException ex) {
        return ResponseEntity.badRequest().body(ApiResponse.error(ErrorCode.PARAM_INVALID, ex.getMessage()));
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodValidation(HandlerMethodValidationException ex) {
        return ResponseEntity.badRequest().body(ApiResponse.error(ErrorCode.PARAM_INVALID, ex.getMessage()));
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class,
            MissingRequestHeaderException.class,
            MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(Exception ex) {
        return ResponseEntity.badRequest().body(ApiResponse.error(ErrorCode.PARAM_INVALID));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(NoResourceFoundException ex) {
        return ResponseEntity.status(ErrorCode.ROUTE_NOT_FOUND.getHttpStatus())
                .body(ApiResponse.error(ErrorCode.ROUTE_NOT_FOUND));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        return ResponseEntity.status(405)
                .body(ApiResponse.error(ErrorCode.ROUTE_NOT_FOUND, "请求方法不支持"));
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<ApiResponse<Void>> handleDuplicateKey(DuplicateKeyException ex) {
        log.warn("duplicate key", ex);
        return ResponseEntity.status(ErrorCode.DATABASE_ERROR.getHttpStatus())
                .body(ApiResponse.error(ErrorCode.DATABASE_ERROR, "数据已存在"));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrity(DataIntegrityViolationException ex) {
        log.warn("data integrity violation", ex);
        return ResponseEntity.status(ErrorCode.DATABASE_ERROR.getHttpStatus())
                .body(ApiResponse.error(ErrorCode.DATABASE_ERROR));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex) {
        log.error("unexpected failure", ex);
        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.getHttpStatus())
                .body(ApiResponse.error(ErrorCode.INTERNAL_ERROR));
    }
}
