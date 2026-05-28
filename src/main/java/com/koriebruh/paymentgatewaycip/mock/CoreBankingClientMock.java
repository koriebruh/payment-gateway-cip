package com.koriebruh.paymentgatewaycip.mock;

import io.micrometer.observation.annotation.Observed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

@Component
public class CoreBankingClientMock implements CoreBankingClient {

    private static final Logger log = LoggerFactory.getLogger(CoreBankingClientMock.class);
    private static final String FAIL_PREFIX = "99";
    private static final long LATENCY_MS = 100L;

    @Override
    @Observed(name = "corebank.client.debit", contextualName = "coreBankClientDebit")
    public CoreBankingResponse debit(String accountNumber, BigDecimal amount, String orderId,
                                     String traceId, String jwtToken) {
        log.info("[CoreBank-Mock] Debit — account={} amount={} orderId={} traceId={} hasToken={}",
                accountNumber, amount, orderId, traceId, jwtToken != null);
        simulateLatency();

        if (accountNumber.startsWith(FAIL_PREFIX)) {
            log.warn("[CoreBank-Mock] Insufficient balance — account={} traceId={}", accountNumber, traceId);
            return new CoreBankingResponse(false, null, "Insufficient balance");
        }

        var ref = "CB-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        log.info("[CoreBank-Mock] Debit success — ref={} traceId={}", ref, traceId);
        return new CoreBankingResponse(true, ref, null);
    }

    private void simulateLatency() {
        try {
            Thread.sleep(LATENCY_MS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
