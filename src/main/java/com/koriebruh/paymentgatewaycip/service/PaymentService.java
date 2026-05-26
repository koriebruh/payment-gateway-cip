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
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Core business logic for payment processing.
 * 
 * <p><b>Financial & Architectural Considerations:</b>
 * <ul>
 *   <li><b>Idempotency:</b> We enforce strict idempotency using {@code orderId} to prevent double-charging users.</li>
 *   <li><b>Distributed Transactions:</b> To prevent DB connection pool exhaustion during slow network calls to CoreBanking/Biller, 
 *       we split the DB writes into discrete, short-lived transactions ({@code REQUIRES_NEW} pattern logic).</li>
 *   <li><b>Circuit Breakers:</b> External financial systems (CoreBank, Biller) are protected by Resilience4j to fail-fast 
 *       during outages, preventing cascading failures in the Gateway.</li>
 *   <li><b>Event-Driven Consistency:</b> Final state changes (SUCCESS/FAILED) are published to Kafka to synchronize downstream 
 *       services (e.g., notification, ledger) without blocking the client response.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final TransactionRepository transactionRepository;
    private final CoreBankingClient coreBankingClient;
    private final BillerClient billerClient;
    private final TransactionEventProducer eventProducer;

    // ── Public API ────────────────────────────────────────────────────────────

    public PaymentResponse processPayment(PaymentRequest request) {
        String traceId = MDC.get("traceId");

        log.info("[{}] Processing payment — orderId={} channel={} amount={}",
                traceId, request.orderId(), request.channel(), request.amount());

        // Enforce idempotency to guarantee a user is never double-charged for the same order
        if (transactionRepository.existsByOrderId(request.orderId())) {
            throw BusinessException.duplicateOrder(request.orderId());
        }

        Transaction.Channel channel = parseChannel(request.channel());

        // Persist initial state before any external money movement begins
        Transaction tx = savePending(request, channel);
        log.info("[{}] Transaction persisted id={} PENDING", traceId, tx.getId());

        // Attempt core banking debit. If this fails, the user's money is safe.
        CoreBankingClient.CoreBankingResponse bankResp =
                callCoreBank(tx.getAccount(), tx.getAmount(), request.orderId());

        if (!bankResp.success()) {
            log.warn("[{}] CoreBank failed — {}", traceId, bankResp.failureReason());
            return failTransaction(tx, bankResp.failureReason(), traceId);
        }
        log.info("[{}] CoreBank success — ref={}", traceId, bankResp.corebankReference());

        // Attempt biller settlement. If this fails, we have debited the user but failed to pay the biller.
        // In a full production system, a failure here requires a reconciliation/refund background job.
        BillerClient.BillerResponse billerResp =
                callBiller(request.orderId(), tx.getAmount(), request.paymentMethod());

        if (!billerResp.success()) {
            log.warn("[{}] Biller failed — {}", traceId, billerResp.failureReason());
            // NOTE: In real-world finance, a reversal/refund to CoreBank must be triggered here asynchronously.
            return failTransaction(tx, billerResp.failureReason(), traceId);
        }
        log.info("[{}] Biller success — ref={}", traceId, billerResp.billerReference());

        return succeedTransaction(tx, bankResp.corebankReference(), billerResp.billerReference(), traceId);
    }

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

    // ── Internal Transaction Boundaries ───────────────────────────────────────
    // These methods use @Transactional to ensure DB writes are committed instantly
    // and DB connections are released *before* the main thread makes the next slow HTTP call.

    @Transactional
    protected Transaction savePending(PaymentRequest request, Transaction.Channel channel) {
        Transaction tx = Transaction.builder()
                .orderId(request.orderId())
                .channel(channel)
                .amount(request.amount())
                .account(request.orderId())   // simplified: use orderId as account key for mock
                .currency(request.currency())
                .paymentMethod(request.paymentMethod())
                .status(Transaction.TransactionStatus.PENDING)
                .build();
        return transactionRepository.save(tx);
    }

    @Transactional
    protected PaymentResponse failTransaction(Transaction tx, String reason, String traceId) {
        tx.setStatus(Transaction.TransactionStatus.FAILED);
        transactionRepository.save(tx);
        eventProducer.publishFailed(TransactionSuccessEvent.from(tx, traceId));

        return new PaymentResponse(
                tx.getId().toString(),
                tx.getOrderId(),
                Transaction.TransactionStatus.FAILED.name(),
                null, null,
                "Transaction failed: " + reason
        );
    }

    @Transactional
    protected PaymentResponse succeedTransaction(Transaction tx, String corebankRef,
                                                 String billerRef, String traceId) {
        tx.setStatus(Transaction.TransactionStatus.SUCCESS);
        tx.setCorebankReference(corebankRef);
        tx.setBillerReference(billerRef);
        transactionRepository.save(tx);
        eventProducer.publishSuccess(TransactionSuccessEvent.from(tx, traceId));

        log.info("[{}] Payment SUCCESS transactionId={}", traceId, tx.getId());
        return new PaymentResponse(
                tx.getId().toString(),
                tx.getOrderId(),
                Transaction.TransactionStatus.SUCCESS.name(),
                corebankRef,
                billerRef,
                "Payment processed successfully"
        );
    }

    // ── Circuit-broken external calls ─────────────────────────────────────────

    @CircuitBreaker(name = "corebank")
    protected CoreBankingClient.CoreBankingResponse callCoreBank(
            String account, BigDecimal amount, String orderId) {
        return coreBankingClient.debit(account, amount, orderId);
    }

    @CircuitBreaker(name = "biller")
    protected BillerClient.BillerResponse callBiller(
            String orderId, BigDecimal amount, String paymentMethod) {
        return billerClient.pay(orderId, amount, paymentMethod);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Transaction.Channel parseChannel(String raw) {
        try {
            return Transaction.Channel.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw BusinessException.invalidChannel(raw);
        }
    }
}
