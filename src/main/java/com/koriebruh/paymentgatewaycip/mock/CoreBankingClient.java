package com.koriebruh.paymentgatewaycip.mock;

import java.math.BigDecimal;

/**
 * Contract for Core Banking debit operations.
 *
 * <p>A production implementation would be a Feign/RestClient adapter targeting
 * the actual core-banking system. {@link CoreBankingClientMock} is active locally.
 */
public interface CoreBankingClient {

    /**
     * Debit {@code amount} from {@code accountNumber}.
     *
     * @param accountNumber account to debit
     * @param amount        positive amount to debit
     * @param orderId       idempotency key to prevent duplicate debits
     */
    CoreBankingResponse debit(String accountNumber, BigDecimal amount, String orderId);

    record CoreBankingResponse(
            boolean success,
            String corebankReference,
            String failureReason
    ) {}
}
