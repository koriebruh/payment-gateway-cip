package com.koriebruh.paymentgatewaycip.service;

import com.koriebruh.paymentgatewaycip.dto.PaymentRequest;
import com.koriebruh.paymentgatewaycip.dto.PaymentResponse;
import com.koriebruh.paymentgatewaycip.entity.Transaction;
import com.koriebruh.paymentgatewaycip.event.model.TransactionSuccessEvent;
import com.koriebruh.paymentgatewaycip.event.producer.TransactionEventProducer;
import com.koriebruh.paymentgatewaycip.exceptions.BusinessException;
import com.koriebruh.paymentgatewaycip.mock.BillerClient;
import com.koriebruh.paymentgatewaycip.mock.CoreBankingClient;
import com.koriebruh.paymentgatewaycip.repository.TransactionRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.observation.annotation.Observed;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Core payment processing orchestrator.
 *
 * <p><b>Financial integrity guarantees:</b>
 * <ul>
 *   <li><b>Idempotency:</b> {@code orderId} uniqueness is enforced before any money movement.</li>
 *   <li><b>Circuit Breaker:</b> CoreBank and Biller calls are protected by Resilience4j to fail-fast.</li>
 *   <li><b>Event sourcing:</b> Final state (SUCCESS/FAILED) is published to Kafka for downstream consistency.</li>
 *   <li><b>Reconciliation note:</b> A biller failure after a successful core-bank debit requires
 *       an async reversal job in production. This is not implemented in this mock.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Observed(name = "payment.service")
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final TransactionRepository    transactionRepository;
    private final CoreBankingClient        coreBankingClient;
    private final BillerClient             billerClient;
    private final TransactionEventProducer eventProducer;
    private final PaymentTransactionHelper transactionHelper;

    @Observed(name = "payment.process", contextualName = "processPayment")
    public PaymentResponse processPayment(PaymentRequest request) {
        String traceId = MDC.get("traceId");

        log.info("[{}] Processing payment — orderId={} channel={} amount={}",
                traceId, request.orderId(), request.channel(), request.amount());

        if (transactionRepository.existsByOrderId(request.orderId())) {
            throw BusinessException.duplicateOrder(request.orderId());
        }

        Transaction.Channel channel = parseChannel(request.channel());
        Transaction tx = transactionHelper.savePending(request, channel);

        log.info("[{}] Transaction persisted id={} status=PENDING", traceId, tx.getId());

        CoreBankingClient.CoreBankingResponse bankResp = callCoreBank(tx.getAccount(), tx.getAmount(), request.orderId());

        if (!bankResp.success()) {
            log.warn("[{}] CoreBank rejected — reason={}", traceId, bankResp.failureReason());
            return transactionHelper.failTransaction(tx, bankResp.failureReason(), traceId);
        }

        log.info("[{}] CoreBank approved — ref={}", traceId, bankResp.corebankReference());

        BillerClient.BillerResponse billerResp = callBiller(request.orderId(), tx.getAmount(), request.paymentMethod());

        if (!billerResp.success()) {
            log.warn("[{}] Biller rejected — reason={}", traceId, billerResp.failureReason());
            return transactionHelper.failTransaction(tx, billerResp.failureReason(), traceId);
        }

        log.info("[{}] Biller approved — ref={}", traceId, billerResp.billerReference());

        return transactionHelper.succeedTransaction(tx, bankResp.corebankReference(), billerResp.billerReference(), traceId);
    }

    @Observed(name = "payment.status", contextualName = "getPaymentStatus")
    @Transactional(readOnly = true)
    public PaymentResponse getStatus(String orderId) {
        Transaction tx = transactionRepository.findByOrderId(orderId)
                .orElseThrow(() -> BusinessException.notFound("Transaction not found: " + orderId));

        return new PaymentResponse(
                tx.getId().toString(),
                tx.getOrderId(),
                tx.getStatus().name(),
                tx.getCorebankReference(),
                tx.getBillerReference(),
                "Transaction status retrieved"
        );
    }

    @CircuitBreaker(name = "corebank")
    @Observed(name = "corebank.debit", contextualName = "coreBankDebit")
    protected CoreBankingClient.CoreBankingResponse callCoreBank(String account, BigDecimal amount, String orderId) {
        return coreBankingClient.debit(account, amount, orderId);
    }

    @CircuitBreaker(name = "biller")
    @Observed(name = "biller.pay", contextualName = "billerPay")
    protected BillerClient.BillerResponse callBiller(String orderId, BigDecimal amount, String paymentMethod) {
        return billerClient.pay(orderId, amount, paymentMethod);
    }

    private Transaction.Channel parseChannel(String raw) {
        try {
            return Transaction.Channel.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw BusinessException.invalidChannel(raw);
        }
    }
}
