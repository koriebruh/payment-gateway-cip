package com.koriebruh.paymentgatewaycip.mock;

import io.micrometer.observation.annotation.Observed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

@Component
public class BillerClientMock implements BillerClient {

    private static final Logger log = LoggerFactory.getLogger(BillerClientMock.class);
    private static final String FAIL_METHOD = "FAIL";
    private static final long LATENCY_MS = 150L;

    @Override
    @Observed(name = "biller.client.pay", contextualName = "billerClientPay")
    public BillerResponse pay(String orderId, BigDecimal amount, String paymentMethod,
                              String traceId, String jwtToken) {
        log.info("[Biller-Mock] Pay — orderId={} amount={} method={} traceId={} hasToken={}",
                orderId, amount, paymentMethod, traceId, jwtToken != null);
        simulateLatency();

        if (FAIL_METHOD.equalsIgnoreCase(paymentMethod)) {
            log.warn("[Biller-Mock] Payment failed — method={} traceId={}", paymentMethod, traceId);
            return new BillerResponse(false, null, "Biller service unavailable");
        }

        var ref = "BL-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        log.info("[Biller-Mock] Payment success — ref={} traceId={}", ref, traceId);
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
