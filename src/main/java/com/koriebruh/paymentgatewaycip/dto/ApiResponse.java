package com.koriebruh.paymentgatewaycip.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Standard API response wrapper used by all endpoints.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        LocalDateTime timestamp,
        String traceId,
        String status,
        String message,
        T data,
        List<String> errors
) {
}
