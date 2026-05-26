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
                "Insufficient balance for account: " + account,
                HttpStatus.UNPROCESSABLE_ENTITY, "INSUFFICIENT_BALANCE");
    }

    public static BusinessException billerFailure(String reason) {
        return new BusinessException(
                "Biller payment failed: " + reason,
                HttpStatus.BAD_GATEWAY, "BILLER_FAILURE");
    }

    public static BusinessException duplicateOrder(String orderId) {
        return new BusinessException(
                "Duplicate order ID: " + orderId,
                HttpStatus.CONFLICT, "DUPLICATE_ORDER");
    }

    public static BusinessException invalidChannel(String channel) {
        return new BusinessException(
                "Unsupported channel: " + channel,
                HttpStatus.BAD_REQUEST, "INVALID_CHANNEL");
    }

    public static BusinessException notFound(String message) {
        return new BusinessException(message, HttpStatus.NOT_FOUND, "NOT_FOUND");
    }
}
