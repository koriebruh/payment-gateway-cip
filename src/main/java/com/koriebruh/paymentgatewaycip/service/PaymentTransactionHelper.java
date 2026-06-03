package com.koriebruh.paymentgatewaycip.service;

import com.koriebruh.paymentgatewaycip.dto.PaymentRequest;
import com.koriebruh.paymentgatewaycip.dto.PaymentResponse;
import com.koriebruh.paymentgatewaycip.entity.Transaction;
import com.koriebruh.paymentgatewaycip.event.model.TransactionSuccessEvent;
import com.koriebruh.paymentgatewaycip.exceptions.BusinessException;
import com.koriebruh.paymentgatewaycip.repository.TransactionRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.koriebruh.paymentgatewaycip.entity.OutboxEvent;
import com.koriebruh.paymentgatewaycip.repository.OutboxEventRepository;
import org.springframework.beans.factory.annotation.Value;

import io.micrometer.observation.annotation.Observed;

/**
 * Handles @Transactional boundary for payment state transitions.
 *
 * <p>Exists as a separate Spring-managed bean so that @Transactional proxying
 * works correctly when called from {@link PaymentService}. Direct self-invocation
 * within the same bean bypasses Spring AOP, making transactions inactive.
 */
@Component
@Observed(name = "payment.transaction.helper")
public class PaymentTransactionHelper {

    private static final Logger log = LoggerFactory.getLogger(PaymentTransactionHelper.class);

    private final TransactionRepository transactionRepository;

    private final OutboxEventRepository outboxEventRepository;

    private final ObjectMapper objectMapper;

    private final String topicCreated;

    private final String topicFailed;

    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
    public PaymentTransactionHelper(
            TransactionRepository transactionRepository,
            OutboxEventRepository outboxEventRepository,
            ObjectMapper objectMapper,
            @Value("${app.kafka.topics.transaction-created}") String topicCreated,
            @Value("${app.kafka.topics.transaction-failed}") String topicFailed) {
        this.transactionRepository = transactionRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
        this.topicCreated = topicCreated;
        this.topicFailed = topicFailed;
    }

    @Transactional(readOnly = true)
    public Optional<PaymentResponse> getExistingIdempotentResponse(String idempotencyKey, PaymentRequest request, String traceId) {
        var existingTxOpt = transactionRepository.findByIdempotencyKey(idempotencyKey);
        if (existingTxOpt.isPresent()) {
            var existingTx = existingTxOpt.get();

            boolean isSamePayload = existingTx.getOrderId().equals(request.orderId()) &&
                    existingTx.getAmount().compareTo(request.amount()) == 0 &&
                    existingTx.getCurrency().equals(request.currency()) &&
                    existingTx.getPaymentMethod().equals(request.paymentMethod()) &&
                    existingTx.getChannel().name().equalsIgnoreCase(request.channel());

            if (!isSamePayload) {
                throw BusinessException.duplicateIdempotencyKey(idempotencyKey);
            }

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
        try {
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
        } catch (DataIntegrityViolationException ex) {
            throw BusinessException.duplicateIdempotencyKey(idempotencyKey);
        }
    }

    @Transactional
    public PaymentResponse failTransaction(Transaction tx, String reason, String traceId) {
        tx.setStatus(Transaction.TransactionStatus.FAILED);
        transactionRepository.save(tx);
        saveOutboxEvent(tx, traceId, topicFailed);

        return new PaymentResponse(
                tx.getId().toString(),
                tx.getOrderId(),
                Transaction.TransactionStatus.FAILED.name(),
                null, null,
                "Transaction failed: %s".formatted(reason)
        );
    }

    @Transactional
    public PaymentResponse succeedTransaction(Transaction tx, String corebankRef,
                                              String billerRef, String traceId) {
        tx.setStatus(Transaction.TransactionStatus.SUCCESS);
        tx.setCorebankReference(corebankRef);
        tx.setBillerReference(billerRef);
        transactionRepository.save(tx);
        saveOutboxEvent(tx, traceId, topicCreated);

        return new PaymentResponse(
                tx.getId().toString(),
                tx.getOrderId(),
                Transaction.TransactionStatus.SUCCESS.name(),
                corebankRef,
                billerRef,
                "Payment processed successfully"
        );
    }

    private void saveOutboxEvent(Transaction tx, String traceId, String topic) {
        try {
            String payload = objectMapper.writeValueAsString(TransactionSuccessEvent.from(tx, traceId));
            OutboxEvent outboxEvent = OutboxEvent.builder()
                    .aggregateType("Transaction")
                    .aggregateId(tx.getId())
                    .eventType(topic)
                    .payload(payload)
                    .build();
            outboxEventRepository.save(outboxEvent);
        } catch (Exception e) {
            throw BusinessException.internalError("Failed to serialize OutboxEvent: %s".formatted(e.getMessage()));
        }
    }
}
