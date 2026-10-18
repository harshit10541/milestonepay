package com.milestonepay.common;

import com.milestonepay.ai.AiClientException;
import com.milestonepay.jobs.IllegalTransitionException;
import com.milestonepay.payments.PayPalException;
import jakarta.persistence.OptimisticLockException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Turns exceptions into {@link ApiError} bodies with the right status, so controllers stay free of
 * try/catch. Anything not listed here is a bug and becomes a plain 500 without internal details.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(UnauthorizedException.class)
    ResponseEntity<ApiError> unauthorized(UnauthorizedException e) {
        return status(HttpStatus.UNAUTHORIZED, "unauthorized", e.getMessage());
    }

    @ExceptionHandler(ForbiddenException.class)
    ResponseEntity<ApiError> forbidden(ForbiddenException e) {
        return status(HttpStatus.FORBIDDEN, "forbidden", e.getMessage());
    }

    @ExceptionHandler(NotFoundException.class)
    ResponseEntity<ApiError> notFound(NotFoundException e) {
        return status(HttpStatus.NOT_FOUND, "not_found", e.getMessage());
    }

    @ExceptionHandler({ConflictException.class, IllegalTransitionException.class})
    ResponseEntity<ApiError> conflict(RuntimeException e) {
        return status(HttpStatus.CONFLICT, "conflict", e.getMessage());
    }

    /** Two requests changed the same milestone at once; the loser can simply retry. */
    @ExceptionHandler({OptimisticLockException.class, ObjectOptimisticLockingFailureException.class})
    ResponseEntity<ApiError> concurrentChange(RuntimeException e) {
        return status(HttpStatus.CONFLICT, "conflict", "The milestone changed at the same time; please retry");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> invalidBody(MethodArgumentNotValidException e) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (FieldError f : e.getBindingResult().getFieldErrors()) {
            fields.putIfAbsent(f.getField(), f.getDefaultMessage());
        }
        return ResponseEntity.badRequest().body(new ApiError("invalid_request", "Some fields are invalid", fields));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            HandlerMethodValidationException.class})
    ResponseEntity<ApiError> unreadable(Exception e) {
        return status(HttpStatus.BAD_REQUEST, "invalid_request", "Malformed request");
    }

    /** Raised by our own value checks, e.g. {@link Money} rejecting an unknown currency. */
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ApiError> badInput(IllegalArgumentException e) {
        return status(HttpStatus.BAD_REQUEST, "invalid_request", e.getMessage());
    }

    @ExceptionHandler(AiClientException.class)
    ResponseEntity<ApiError> aiFailed(AiClientException e) {
        log.warn("AI service call failed: {}", e.getMessage());
        return status(HttpStatus.BAD_GATEWAY, "ai_unavailable", e.getMessage());
    }

    /** PayPal failed or answered something we can't use. Logged with PayPal's debug id for support. */
    @ExceptionHandler(PayPalException.class)
    ResponseEntity<ApiError> paypalFailed(PayPalException e) {
        log.warn("PayPal call failed: {}", e.getMessage());
        return status(HttpStatus.BAD_GATEWAY, "paypal_error", e.getMessage());
    }

    private static ResponseEntity<ApiError> status(HttpStatus status, String error, String message) {
        return ResponseEntity.status(status).body(ApiError.of(error, message));
    }
}
