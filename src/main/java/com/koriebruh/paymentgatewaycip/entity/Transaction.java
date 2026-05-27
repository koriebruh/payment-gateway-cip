package com.koriebruh.paymentgatewaycip.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "transactions",
        indexes = {
                @Index(name = "idx_transactions_order_id", columnList = "order_id"),
                @Index(name = "idx_transactions_status", columnList = "status")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transaction {

    @Id
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "order_id", nullable = false, unique = true, length = 255)
    private String orderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 50)
    private Channel channel;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "account", nullable = false, length = 50)
    private String account;

    @Column(name = "currency", length = 10, columnDefinition = "VARCHAR(10) DEFAULT 'IDR'")
    @Builder.Default
    private String currency = "IDR";

    @Column(name = "payment_method", length = 50)
    private String paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TransactionStatus status;

    /**
     * Reference number from the core banking system after a successful debit.
     * Populated only on SUCCESS; null on PENDING or FAILED.
     */
    @Column(name = "corebank_reference", length = 255)
    private String corebankReference;

    /**
     * Reference number from the biller aggregator after a successful payment forward.
     * Populated only on SUCCESS; null on PENDING or FAILED.
     */
    @Column(name = "biller_reference", length = 255)
    private String billerReference;

    @Column(name = "created_at", updatable = false,
            columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
    private LocalDateTime createdAt;

    @Column(name = "updated_at",
            columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (this.createdAt == null) this.createdAt = now;
        if (this.updatedAt == null) this.updatedAt = now;
        if (this.currency == null) this.currency = "IDR";
        if (this.status == null) this.status = TransactionStatus.PENDING;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public enum Channel {
        MOBILE_BANKING,
        INTERNET_BANKING,
        ATM
    }

    public enum TransactionStatus {
        PENDING,
        SUCCESS,
        FAILED
    }
}
