package com.koriebruh.paymentgatewaycip.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response payload for payment operations.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PaymentResponse(
        @JsonProperty("transaction_id")
        String transactionId,

        @JsonProperty("order_id")
        String orderId,

        String status,

        @JsonProperty("corebank_reference")
        String corebankReference,

        @JsonProperty("biller_reference")
        String billerReference,

        String message
) {
}
