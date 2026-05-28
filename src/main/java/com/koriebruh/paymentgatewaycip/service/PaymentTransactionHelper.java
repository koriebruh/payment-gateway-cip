package com.koriebruh.paymentgatewaycip.service;

import com.koriebruh.paymentgatewaycip.dto.PaymentRequest;
import com.koriebruh.paymentgatewaycip.dto.PaymentResponse;
import com.koriebruh.paymentgatewaycip.entity.Transaction;
import com.koriebruh.paymentgatewaycip.event.model.TransactionSuccessEvent;
import com.koriebruh.paymentgatewaycip.event.producer.TransactionEventProducer;
import com.koriebruh.paymentgatewaycip.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Handles @Transactional boundary for payment state transitions.
 *
 * <p>Exists as a separate Spring-managed bean so that @Transactional proxying
 * works correctly when called from {@link PaymentService}. Direct self-invocation
 * within the same bean bypasses Spring AOP, making transactions inactive.
 */
@Component
@RequiredArgsConstructor
public class PaymentTransactionHelper {

    private static final Logger log = LoggerFactory.getLogger(PaymentTransactionHelper.class);

    private final TransactionRepository    transactionRepository;

    private final TransactionEventProducer eventProducer;

    @Transactional(readOnly = true)
    public Optional<PaymentResponse> getExistingIdempotentResponse(String idempotencyKey, String traceId) {
        var existingTxOpt = transactionRepository.findByIdempotencyKey(idempotencyKey);
        if (existingTxOpt.isPresent()) {
            var existingTx = existingTxOpt.get();
            log.info("Idempotent request received, returning existing transaction id={} status={} traceId={}",
                    existingTx.getId(), existingTx.getStatus(), traceId);
            return Optional.of(new PaymentResponse(
                    existingTx.getId(),
                    existingTx.getOrderId(),
                    existingTx.getStatus().name(),
                    existingTx.getCorebankReference(),
                    existingTx.getBillerReference(),
                    "Payment processed successfully (Idempotent response)"
            ));
        }
        return Optional.empty();
    }

    @Transactional
    public Transaction savePending(PaymentRequest request, Transaction.Channel channel, String idempotencyKey) {
        Transaction tx = Transaction.builder()
                .idempotencyKey(idempotencyKey)
                .orderId(request.orderId())
                .channel(channel)
                .amount(request.amount())
                .account(request.orderId())
                .currency(request.currency())
                .paymentMethod(request.paymentMethod())
                .status(Transaction.TransactionStatus.PENDING)
                .build();
        return transactionRepository.save(tx);
    }

    @Transactional
    public PaymentResponse failTransaction(Transaction tx, String reason, String traceId) {
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
    public PaymentResponse succeedTransaction(Transaction tx, String corebankRef,
                                              String billerRef, String traceId) {
        tx.setStatus(Transaction.TransactionStatus.SUCCESS);
        tx.setCorebankReference(corebankRef);
        tx.setBillerReference(billerRef);
        transactionRepository.save(tx);
        eventProducer.publishSuccess(TransactionSuccessEvent.from(tx, traceId));

        return new PaymentResponse(
                tx.getId().toString(),
                tx.getOrderId(),
                Transaction.TransactionStatus.SUCCESS.name(),
                corebankRef,
                billerRef,
                "Payment processed successfully"
        );
    }
}
