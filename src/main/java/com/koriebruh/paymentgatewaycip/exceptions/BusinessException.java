package com.koriebruh.paymentgatewaycip.exceptions;

import org.springframework.http.HttpStatus;

/**
 * Domain exception representing a known business rule violation.
 * Maps to a specific HTTP status and error code for structured error responses.
 */
public class BusinessException extends RuntimeException {

    private final HttpStatus status;
    private final String errorCode;

    public BusinessException(String message, HttpStatus status, String errorCode) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }

    public BusinessException(String message, HttpStatus status, String errorCode, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.errorCode = errorCode;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getErrorCode() {
        return errorCode;
    }

    // ── Named factory methods ────────────────────────────────────────────────

    public static BusinessException insufficientBalance(String account) {
        return new BusinessException(
                "Insufficient balance for account: %s".formatted(account),
                HttpStatus.UNPROCESSABLE_ENTITY, "INSUFFICIENT_BALANCE");
    }

    public static BusinessException billerFailure(String reason) {
        return new BusinessException(
                "Biller payment failed: %s".formatted(reason),
                HttpStatus.BAD_GATEWAY, "BILLER_FAILURE");
    }

    public static BusinessException duplicateOrder(String orderId) {
        return new BusinessException(
                "Duplicate order ID: %s".formatted(orderId),
                HttpStatus.CONFLICT, "DUPLICATE_ORDER");
    }

    public static BusinessException duplicateIdempotencyKey(String idempotencyKey) {
        return new BusinessException(
                "Duplicate idempotency key used with different payload: %s".formatted(idempotencyKey),
                HttpStatus.CONFLICT, "DUPLICATE_IDEMPOTENCY_KEY");
    }

    public static BusinessException invalidChannel(String channel) {
        return new BusinessException(
                "Unsupported channel: %s".formatted(channel),
                HttpStatus.BAD_REQUEST, "INVALID_CHANNEL");
    }

    public static BusinessException notFound(String message) {
        return new BusinessException(message, HttpStatus.NOT_FOUND, "NOT_FOUND");
    }

    public static BusinessException unauthorized() {
        return new BusinessException("Unauthorized request", HttpStatus.UNAUTHORIZED, "UNAUTHORIZED");
    }

    public static BusinessException internalError(String message) {
        return new BusinessException("Internal Server Error: %s".formatted(message), HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR");
    }

    public static BusinessException serviceUnavailable(String service) {
        return new BusinessException("Service Unavailable: %s".formatted(service), HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE");
    }
}
