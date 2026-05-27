package com.koriebruh.paymentgatewaycip.mock;

import java.math.BigDecimal;

/**
 * Contract for Biller Aggregator payment operations.
 *
 * <p>A production implementation would be a Feign/RestClient adapter targeting
 * the actual biller aggregator. {@link BillerClientMock} is active locally.
 */
public interface BillerClient {

    /**
     * Forward a payment to the biller aggregator.
     *
     * @param orderId       idempotency key for the payment
     * @param amount        payment amount
     * @param paymentMethod e.g. {@code VIRTUAL_ACCOUNT}, {@code QRIS}
     */
    BillerResponse pay(String orderId, BigDecimal amount, String paymentMethod);

    record BillerResponse(
            boolean success,
            String  billerReference,
            String  failureReason
    ) {}
}
