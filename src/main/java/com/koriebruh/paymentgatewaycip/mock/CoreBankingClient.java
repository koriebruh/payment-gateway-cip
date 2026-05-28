package com.koriebruh.paymentgatewaycip.mock;

import java.math.BigDecimal;

public interface CoreBankingClient {

    CoreBankingResponse debit(String accountNumber, BigDecimal amount, String orderId,
                              String traceId, String jwtToken);

    record CoreBankingResponse(
            boolean success,
            String corebankReference,
            String failureReason
    ) {}
}
