package com.koriebruh.paymentgatewaycip.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Standard API response wrapper used by all endpoints.
 * Explicit all-args constructor + static builder — no Lombok dependency.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private final LocalDateTime timestamp;
    private final String traceId;
    private final String status;
    private final String message;
    private final T data;
    private final List<String> errors;

    // All-args constructor
    public ApiResponse(LocalDateTime timestamp, String traceId, String status,
                       String message, T data, List<String> errors) {
        this.timestamp = timestamp;
        this.traceId = traceId;
        this.status = status;
        this.message = message;
        this.data = data;
        this.errors = errors;
    }

    // Getters
    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public String getTraceId() {
        return traceId;
    }

    public String getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public T getData() {
        return data;
    }

    public List<String> getErrors() {
        return errors;
    }

    // ── Builder ──────────────────────────────────────────────────────────────

    public static <T> Builder<T> builder() {
        return new Builder<>();
    }

    public static final class Builder<T> {
        private LocalDateTime timestamp;
        private String traceId;
        private String status;
        private String message;
        private T data;
        private List<String> errors;

        public Builder<T> timestamp(LocalDateTime v) {
            this.timestamp = v;
            return this;
        }

        public Builder<T> traceId(String v) {
            this.traceId = v;
            return this;
        }

        public Builder<T> status(String v) {
            this.status = v;
            return this;
        }

        public Builder<T> message(String v) {
            this.message = v;
            return this;
        }

        public Builder<T> data(T v) {
            this.data = v;
            return this;
        }

        public Builder<T> errors(List<String> v) {
            this.errors = v;
            return this;
        }

        public ApiResponse<T> build() {
            return new ApiResponse<>(timestamp, traceId, status, message, data, errors);
        }
    }
}
