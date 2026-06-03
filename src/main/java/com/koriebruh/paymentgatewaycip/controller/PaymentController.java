package com.koriebruh.paymentgatewaycip.controller;

import com.koriebruh.paymentgatewaycip.dto.ApiResponse;
import com.koriebruh.paymentgatewaycip.dto.ApiResponseFactory;
import com.koriebruh.paymentgatewaycip.dto.PaymentRequest;
import com.koriebruh.paymentgatewaycip.dto.PaymentResponse;
import com.koriebruh.paymentgatewaycip.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/payments")
@RequiredArgsConstructor
@Tag(name = "Payments", description = "Payment processing operations")
@SecurityRequirement(name = "bearerAuth")
public class PaymentController {

    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);

    private final PaymentService paymentService;
    private final ApiResponseFactory responseFactory;

    @Operation(summary = "Process a payment",
            description = "Debit account via CoreBank, forward to Biller, return final status.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Payment processed"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation error"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Duplicate orderId"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "Insufficient balance"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "502", description = "Biller failure"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503", description = "Circuit breaker open")
    })
    @PostMapping
    public ResponseEntity<ApiResponse<PaymentResponse>> processPayment(
            @Valid @RequestBody PaymentRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {

        log.info("event=process_payment orderId={} idempotencyKey={}", request.orderId(), idempotencyKey);
        PaymentResponse result = paymentService.processPayment(request, idempotencyKey);

        if ("FAILED".equals(result.status())) {
            HttpStatus status = HttpStatus.BAD_REQUEST;
            if (result.message() != null && result.message().contains("Insufficient balance")) {
                status = HttpStatus.UNPROCESSABLE_ENTITY;
            } else if (result.message() != null && result.message().contains("Biller")) {
                status = HttpStatus.BAD_GATEWAY;
            }
            return ResponseEntity.status(status)
                    .body(responseFactory.error(result, "Payment processing failed"));
        }

        HttpStatus successStatus = (result.message() != null && result.message().contains("Idempotent")) 
                ? HttpStatus.OK : HttpStatus.CREATED;

        return ResponseEntity.status(successStatus)
                .body(responseFactory.success(result, "Payment processed"));
    }


    @Operation(summary = "Get transaction status", description = "Returns status and references for an orderId.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Transaction found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Not found")
    })
    @GetMapping("/{orderId}")
    public ResponseEntity<ApiResponse<PaymentResponse>> getStatus(@PathVariable String orderId) {
        log.info("event=get_payment_status orderId={}", orderId);
        return ResponseEntity.ok(responseFactory.success(paymentService.getStatus(orderId)));
    }
}
