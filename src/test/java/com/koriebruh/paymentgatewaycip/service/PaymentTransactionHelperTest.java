package com.koriebruh.paymentgatewaycip.service;

import com.koriebruh.paymentgatewaycip.dto.PaymentRequest;
import com.koriebruh.paymentgatewaycip.dto.PaymentResponse;
import com.koriebruh.paymentgatewaycip.entity.OutboxEvent;
import com.koriebruh.paymentgatewaycip.entity.Transaction;
import com.koriebruh.paymentgatewaycip.repository.OutboxEventRepository;
import com.koriebruh.paymentgatewaycip.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentTransactionHelperTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private OutboxEventRepository outboxEventRepository;


    @InjectMocks
    private PaymentTransactionHelper helper;

    @Captor
    private ArgumentCaptor<Transaction> txCaptor;

    @Captor
    private ArgumentCaptor<OutboxEvent> outboxCaptor;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(helper, "topicCreated", "payment.transaction.created");
        ReflectionTestUtils.setField(helper, "topicFailed", "payment.transaction.failed");
    }

    @Test
    void getExistingIdempotentResponse_WhenExists_ShouldReturnResponse() {
        Transaction tx = Transaction.builder()
                .id(UUID.randomUUID().toString())
                .orderId("ORD-1")
                .status(Transaction.TransactionStatus.SUCCESS)
                .corebankReference("CB-1")
                .billerReference("BL-1")
                .build();
        
        when(transactionRepository.findByIdempotencyKey("IDEMP-1")).thenReturn(Optional.of(tx));

        Optional<PaymentResponse> response = helper.getExistingIdempotentResponse("IDEMP-1", "trace-123");

        assertThat(response).isPresent();
        assertThat(response.get().status()).isEqualTo("SUCCESS");
        assertThat(response.get().corebankReference()).isEqualTo("CB-1");
    }

    @Test
    void getExistingIdempotentResponse_WhenNotExists_ShouldReturnEmpty() {
        when(transactionRepository.findByIdempotencyKey("IDEMP-1")).thenReturn(Optional.empty());

        Optional<PaymentResponse> response = helper.getExistingIdempotentResponse("IDEMP-1", "trace-123");

        assertThat(response).isEmpty();
    }

    @Test
    void savePending_ShouldSaveAndReturnTransaction() {
        PaymentRequest request = new PaymentRequest("ORD-1", "MOBILE_BANKING", BigDecimal.TEN, "IDR", "VA");
        
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        Transaction tx = helper.savePending(request, Transaction.Channel.MOBILE_BANKING, "IDEMP-1");

        assertThat(tx).isNotNull();
        assertThat(tx.getOrderId()).isEqualTo("ORD-1");
        assertThat(tx.getStatus()).isEqualTo(Transaction.TransactionStatus.PENDING);
        assertThat(tx.getIdempotencyKey()).isEqualTo("IDEMP-1");
        assertThat(tx.getChannel()).isEqualTo(Transaction.Channel.MOBILE_BANKING);
        
        verify(transactionRepository).save(any(Transaction.class));
    }

    @Test
    void failTransaction_ShouldUpdateStatusAndSaveOutboxEvent() {
        Transaction tx = Transaction.builder()
                .id(UUID.randomUUID().toString())
                .orderId("ORD-1")
                .channel(Transaction.Channel.MOBILE_BANKING)
                .status(Transaction.TransactionStatus.PENDING)
                .build();

        PaymentResponse response = helper.failTransaction(tx, "Error occurred", "trace-123");

        assertThat(response.status()).isEqualTo("FAILED");
        assertThat(response.message()).contains("Error occurred");

        verify(transactionRepository).save(txCaptor.capture());
        assertThat(txCaptor.getValue().getStatus()).isEqualTo(Transaction.TransactionStatus.FAILED);

        verify(outboxEventRepository).save(outboxCaptor.capture());
        OutboxEvent outboxEvent = outboxCaptor.getValue();
        assertThat(outboxEvent.getTopic()).isEqualTo("payment.transaction.failed");
        assertThat(outboxEvent.getAggregateId()).isEqualTo(tx.getId());
        assertThat(outboxEvent.getPayload()).contains("ORD-1");
        assertThat(outboxEvent.getPayload()).contains("FAILED");
    }

    @Test
    void succeedTransaction_ShouldUpdateStatusAndSaveOutboxEvent() {
        Transaction tx = Transaction.builder()
                .id(UUID.randomUUID().toString())
                .orderId("ORD-1")
                .channel(Transaction.Channel.MOBILE_BANKING)
                .status(Transaction.TransactionStatus.PENDING)
                .build();

        PaymentResponse response = helper.succeedTransaction(tx, "CB-1", "BL-1", "trace-123");

        assertThat(response.status()).isEqualTo("SUCCESS");
        assertThat(response.corebankReference()).isEqualTo("CB-1");
        assertThat(response.billerReference()).isEqualTo("BL-1");

        verify(transactionRepository).save(txCaptor.capture());
        assertThat(txCaptor.getValue().getStatus()).isEqualTo(Transaction.TransactionStatus.SUCCESS);
        assertThat(txCaptor.getValue().getCorebankReference()).isEqualTo("CB-1");

        verify(outboxEventRepository).save(outboxCaptor.capture());
        OutboxEvent outboxEvent = outboxCaptor.getValue();
        assertThat(outboxEvent.getTopic()).isEqualTo("payment.transaction.created");
        assertThat(outboxEvent.getAggregateId()).isEqualTo(tx.getId());
        assertThat(outboxEvent.getPayload()).contains("SUCCESS");
    }
}
