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
        log.warn("traceId={} event=business_exception code={} msg={}", traceId(), ex.getErrorCode(), ex.getMessage());
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
        log.warn("traceId={} event=validation_failed errors={}", traceId(), errors);
        return ResponseEntity.badRequest().body(buildError("Validation failed", errors));
    }

    @ExceptionHandler(org.springframework.web.bind.MissingRequestHeaderException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingHeader(org.springframework.web.bind.MissingRequestHeaderException ex) {
        log.warn("traceId={} event=missing_header header={}", traceId(), ex.getHeaderName());
        return ResponseEntity.badRequest().body(buildError("Missing required header: %s".formatted(ex.getHeaderName()), List.of("MISSING_HEADER")));
    }

    @ExceptionHandler(io.github.resilience4j.circuitbreaker.CallNotPermittedException.class)
    public ResponseEntity<ApiResponse<Void>> handleCircuitOpen(
            io.github.resilience4j.circuitbreaker.CallNotPermittedException ex) {
        log.error("traceId={} event=circuit_open msg={}", traceId(), ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(buildError("External service temporarily unavailable. Please retry.", List.of("CIRCUIT_OPEN")));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneric(Exception ex) {
        log.error("traceId={} event=unhandled_exception msg={}", traceId(), ex.getMessage(), ex);
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
