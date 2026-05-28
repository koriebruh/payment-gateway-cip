package com.koriebruh.paymentgatewaycip.mock;

import java.math.BigDecimal;

public interface BillerClient {

    BillerResponse pay(String orderId, BigDecimal amount, String paymentMethod,
                       String traceId, String jwtToken);

    record BillerResponse(
            boolean success,
            String  billerReference,
            String  failureReason
    ) {}
}
