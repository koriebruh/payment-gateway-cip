package com.koriebruh.paymentgatewaycip.event.model;

import com.koriebruh.paymentgatewaycip.entity.Transaction;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Immutable event published to Kafka when a transaction completes successfully.
 *
 * <p>Using a Java {@code record} ensures the event payload is a pure value object
 * — immutable, auto-generating equals/hashCode/toString, and concise.
 *
 * <p>Topic: {@code payment.transaction.created}
 *
 * @param transactionId    UUID of the persisted transaction
 * @param orderId          business order identifier
 * @param account          debited account number
 * @param amount           transaction amount
 * @param currency         ISO-4217 currency code (e.g., IDR)
 * @param paymentMethod    e.g. VIRTUAL_ACCOUNT, QRIS
 * @param channel          e.g. MOBILE_BANKING, ATM
 * @param corebankReference reference from core-banking debit
 * @param billerReference  reference from biller aggregator
 * @param status           final transaction status
 * @param occurredAt       timestamp when the event was generated
 * @param traceId          distributed trace identifier from MDC
 */
public record TransactionSuccessEvent(
        String         transactionId,
        String         orderId,
        String         account,
        BigDecimal     amount,
        String         currency,
        String         paymentMethod,
        String         channel,
        String         corebankReference,
        String         billerReference,
        String         status,
        LocalDateTime  occurredAt,
        String         traceId
) {

    /** Factory method — builds the event directly from a persisted {@link Transaction}. */
    public static TransactionSuccessEvent from(Transaction tx, String traceId) {
        return new TransactionSuccessEvent(
                tx.getId(),
                tx.getOrderId(),
                tx.getAccount(),
                tx.getAmount(),
                tx.getCurrency(),
                tx.getPaymentMethod(),
                tx.getChannel().name(),
                tx.getCorebankReference(),
                tx.getBillerReference(),
                tx.getStatus().name(),
                LocalDateTime.now(),
                traceId
        );
    }
}
