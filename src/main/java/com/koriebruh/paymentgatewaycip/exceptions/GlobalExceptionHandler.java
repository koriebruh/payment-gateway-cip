package com.koriebruh.paymentgatewaycip.exceptions;

import com.koriebruh.paymentgatewaycip.dto.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException ex) {
        log.warn("[{}] Business exception — code={} msg={}", traceId(), ex.getErrorCode(), ex.getMessage());
        return ResponseEntity
                .status(ex.getStatus())
                .body(buildError(ex.getMessage(), List.of(ex.getErrorCode())));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        List<String> errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(FieldError::getDefaultMessage)
                .toList();
        log.warn("[{}] Validation failed — errors={}", traceId(), errors);
        return ResponseEntity.badRequest().body(buildError("Validation failed", errors));
    }

    @ExceptionHandler(io.github.resilience4j.circuitbreaker.CallNotPermittedException.class)
    public ResponseEntity<ApiResponse<Void>> handleCircuitOpen(
            io.github.resilience4j.circuitbreaker.CallNotPermittedException ex) {
        log.error("[{}] Circuit breaker OPEN — {}", traceId(), ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(buildError("External service temporarily unavailable. Please retry.", List.of("CIRCUIT_OPEN")));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneric(Exception ex) {
        log.error("[{}] Unhandled exception — {}", traceId(), ex.getMessage(), ex);
        return ResponseEntity
                .internalServerError()
                .body(buildError("An unexpected error occurred.", List.of()));
    }

    private ApiResponse<Void> buildError(String message, List<String> errors) {
        return new ApiResponse<>(LocalDateTime.now(), traceId(), "FAILED", message, null, errors);
    }

    private String traceId() {
        String id = MDC.get("traceId");
        return id != null ? id : "n/a";
    }
}
