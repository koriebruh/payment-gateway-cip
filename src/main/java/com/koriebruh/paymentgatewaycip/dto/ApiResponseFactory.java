package com.koriebruh.paymentgatewaycip.dto;

import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Factory for building consistent {@link ApiResponse} wrappers.
 *
 * <p>Automatically populates the {@code traceId} from SLF4J MDC so every
 * response — success or error — is traceable back to its originating request
 * without extra boilerplate in controllers.
 */
@Component
public class ApiResponseFactory {

    public <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(
                LocalDateTime.now(),
                traceId(),
                "SUCCESS",
                "Operation successful",
                data,
                null
        );
    }

    public <T> ApiResponse<T> success(T data, String message) {
        return new ApiResponse<>(
                LocalDateTime.now(),
                traceId(),
                "SUCCESS",
                message,
                data,
                null
        );
    }

    public <T> ApiResponse<T> error(String message, List<String> errors) {
        return new ApiResponse<>(
                LocalDateTime.now(),
                traceId(),
                "FAILED",
                message,
                null,
                errors
        );
    }

    public <T> ApiResponse<T> error(T data, String message) {
        return new ApiResponse<>(
                LocalDateTime.now(),
                traceId(),
                "FAILED",
                message,
                data,
                null
        );
    }

    // -----------------------------------------------------------------------

    private String traceId() {
        String id = MDC.get("traceId");
        return id != null ? id : "n/a";
    }
}
