package com.koriebruh.paymentgatewaycip.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record PaymentRequest(
        @JsonProperty("order_id")
        @NotBlank(message = "Order ID is required")
        String orderId,

        @NotBlank(message = "Channel is required")
        String channel,

        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
        BigDecimal amount,

        @NotBlank(message = "Currency is required")
        @Size(min = 3, max = 3, message = "Currency must be 3 characters")
        String currency,

        @JsonProperty("payment_method")
        @NotBlank(message = "Payment method is required")
        String paymentMethod
) {
}