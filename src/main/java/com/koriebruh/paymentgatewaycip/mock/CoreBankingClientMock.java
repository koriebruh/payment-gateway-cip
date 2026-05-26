package com.koriebruh.paymentgatewaycip.mock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Mock implementation of {@link CoreBankingClient} for local development.
 * Accounts starting with "99" always fail (insufficient balance).
 * 100ms artificial latency simulates network round-trip.
 */
@Component
public class CoreBankingClientMock implements CoreBankingClient {

    private static final Logger log = LoggerFactory.getLogger(CoreBankingClientMock.class);
    private static final String FAIL_PREFIX = "99";
    private static final long LATENCY_MS = 100L;

    @Override
    public CoreBankingResponse debit(String accountNumber, BigDecimal amount, String orderId) {
        log.info("[CoreBank-Mock] Debit — account={} amount={} orderId={}", accountNumber, amount, orderId);
        simulateLatency();

        if (accountNumber.startsWith(FAIL_PREFIX)) {
            log.warn("[CoreBank-Mock] Insufficient balance — account={}", accountNumber);
            return new CoreBankingResponse(false, null, "Insufficient balance");
        }

        String ref = "CB-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        log.info("[CoreBank-Mock] Debit success — ref={}", ref);
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
