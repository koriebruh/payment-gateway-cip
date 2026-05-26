package com.koriebruh.paymentgatewaycip.mock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Mock implementation of {@link BillerClient} for local development.
 * Payment method "FAIL" always fails. 150ms artificial latency.
 */
@Component
public class BillerClientMock implements BillerClient {

    private static final Logger log = LoggerFactory.getLogger(BillerClientMock.class);
    private static final String FAIL_METHOD = "FAIL";
    private static final long LATENCY_MS = 150L;

    @Override
    public BillerResponse pay(String orderId, BigDecimal amount, String paymentMethod) {
        log.info("[Biller-Mock] Pay — orderId={} amount={} method={}", orderId, amount, paymentMethod);
        simulateLatency();

        if (FAIL_METHOD.equalsIgnoreCase(paymentMethod)) {
            log.warn("[Biller-Mock] Payment failed — method={}", paymentMethod);
            return new BillerResponse(false, null, "Biller service unavailable");
        }

        String ref = "BL-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        log.info("[Biller-Mock] Payment success — ref={}", ref);
        return new BillerResponse(true, ref, null);
    }

    private void simulateLatency() {
        try {
            Thread.sleep(LATENCY_MS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
