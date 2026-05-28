package com.koriebruh.paymentgatewaycip.exceptions;

import com.koriebruh.paymentgatewaycip.dto.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    void handleBusiness_ShouldReturnCorrectStatusAndBody() {
        BusinessException ex = new BusinessException("Error msg", HttpStatus.NOT_FOUND, "NOT_FOUND");
        
        ResponseEntity<ApiResponse<Void>> response = handler.handleBusiness(ex);
        
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("Error msg");
        assertThat(response.getBody().getErrors()).containsExactly("NOT_FOUND");
    }

    @Test
    void handleValidation_ShouldReturn400AndExtractErrors() {
        BindingResult bindingResult = mock(BindingResult.class);
        FieldError fieldError = new FieldError("object", "field", "must not be blank");
        when(bindingResult.getFieldErrors()).thenReturn(List.of(fieldError));
        
        MethodParameter parameter = mock(MethodParameter.class);
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(parameter, bindingResult);
        
        ResponseEntity<ApiResponse<Void>> response = handler.handleValidation(ex);
        
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("Validation failed");
        assertThat(response.getBody().getErrors()).containsExactly("must not be blank");
    }

    @Test
    void handleMissingHeader_ShouldReturn400() {
        MissingRequestHeaderException ex = new MissingRequestHeaderException("Idempotency-Key", mock(MethodParameter.class));
        
        ResponseEntity<ApiResponse<Void>> response = handler.handleMissingHeader(ex);
        
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).contains("Missing required header: Idempotency-Key");
        assertThat(response.getBody().getErrors()).containsExactly("MISSING_HEADER");
    }

    @Test
    void handleCircuitOpen_ShouldReturn503() {
        io.github.resilience4j.circuitbreaker.CircuitBreaker circuitBreaker = mock(io.github.resilience4j.circuitbreaker.CircuitBreaker.class);
        when(circuitBreaker.getName()).thenReturn("test-breaker");
        when(circuitBreaker.getCircuitBreakerConfig()).thenReturn(io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.ofDefaults());
        
        io.github.resilience4j.circuitbreaker.CallNotPermittedException ex = 
                io.github.resilience4j.circuitbreaker.CallNotPermittedException.createCallNotPermittedException(circuitBreaker);
                
        ResponseEntity<ApiResponse<Void>> response = handler.handleCircuitOpen(ex);
        
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getErrors()).containsExactly("CIRCUIT_OPEN");
    }

    @Test
    void handleGeneric_ShouldReturn500() {
        Exception ex = new Exception("Unknown error");
        
        ResponseEntity<ApiResponse<Void>> response = handler.handleGeneric(ex);
        
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("An unexpected error occurred.");
    }
}
