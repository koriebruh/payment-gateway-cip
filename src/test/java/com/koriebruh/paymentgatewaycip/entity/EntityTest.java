package com.koriebruh.paymentgatewaycip.entity;

import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EntityTest {

    @Test
    void transaction_onCreate_ShouldSetDefaultsWhenNull() {
        Transaction tx = new Transaction();
        tx.onCreate();
        
        assertThat(tx.getId()).isNotNull().startsWith("TX-");
        assertThat(tx.getCreatedAt()).isNotNull();
        assertThat(tx.getUpdatedAt()).isNotNull();
        assertThat(tx.getCurrency()).isEqualTo("IDR");
        assertThat(tx.getStatus()).isEqualTo(Transaction.TransactionStatus.PENDING);
    }

    @Test
    void transaction_onCreate_ShouldKeepExistingValuesWhenNotNull() {
        LocalDateTime time = LocalDateTime.of(2025, 1, 1, 12, 0);
        Transaction tx = new Transaction();
        tx.setId("TX-1234");
        tx.setCreatedAt(time);
        tx.setUpdatedAt(time);
        tx.setCurrency("USD");
        tx.setStatus(Transaction.TransactionStatus.SUCCESS);
        
        tx.onCreate();
        
        assertThat(tx.getId()).isEqualTo("TX-1234");
        assertThat(tx.getCreatedAt()).isEqualTo(time);
        assertThat(tx.getUpdatedAt()).isEqualTo(time);
        assertThat(tx.getCurrency()).isEqualTo("USD");
        assertThat(tx.getStatus()).isEqualTo(Transaction.TransactionStatus.SUCCESS);
    }
    
    @Test
    void transaction_onUpdate_ShouldUpdateTimestamp() {
        Transaction tx = new Transaction();
        tx.onUpdate();
        assertThat(tx.getUpdatedAt()).isNotNull();
    }

    @Test
    void outboxEvent_onCreate_ShouldSetDefaultsWhenNull() {
        OutboxEvent event = new OutboxEvent();
        event.onCreate();
        
        assertThat(event.getId()).isNotNull();
        assertThat(event.getCreatedAt()).isNotNull();
    }

    @Test
    void outboxEvent_onCreate_ShouldKeepExistingValuesWhenNotNull() {
        UUID id = UUID.randomUUID();
        LocalDateTime time = LocalDateTime.of(2025, 1, 1, 12, 0);
        
        OutboxEvent event = new OutboxEvent();
        event.setId(id);
        event.setCreatedAt(time);
        
        event.onCreate();
        
        assertThat(event.getId()).isEqualTo(id);
        assertThat(event.getCreatedAt()).isEqualTo(time);
    }
}
