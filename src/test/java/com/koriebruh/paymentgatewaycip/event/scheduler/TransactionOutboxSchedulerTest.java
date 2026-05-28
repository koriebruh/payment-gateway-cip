package com.koriebruh.paymentgatewaycip.event.scheduler;

import com.koriebruh.paymentgatewaycip.entity.OutboxEvent;
import com.koriebruh.paymentgatewaycip.repository.OutboxEventRepository;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionOutboxSchedulerTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private TransactionOutboxScheduler scheduler;

    @Test
    void processOutbox_WhenNoEvents_ShouldDoNothing() {
        when(outboxEventRepository.findAll(any(PageRequest.class)))
                .thenReturn(new PageImpl<>(Collections.emptyList()));

        scheduler.processOutbox();

        verify(kafkaTemplate, never()).send(anyString(), any(), any());
        verify(outboxEventRepository, never()).delete(any());
    }

    @Test
    void processOutbox_WhenEventsExist_ShouldSendToKafkaAndDelete() {
        UUID eventId = UUID.randomUUID();
        OutboxEvent event = OutboxEvent.builder()
                .id(UUID.randomUUID())
                .aggregateId(eventId.toString())
                .topic("test.topic")
                .payload("{\"key\":\"value\"}")
                .build();

        when(outboxEventRepository.findAll(any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(event)));

        ProducerRecord<String, Object> producerRecord = new ProducerRecord<>("test.topic", eventId.toString(), "{\"key\":\"value\"}");
        RecordMetadata metadata = new RecordMetadata(new TopicPartition("test.topic", 0), 0L, 0, 0L, 0, 0);
        SendResult<String, Object> sendResult = new SendResult<>(producerRecord, metadata);
        
        when(kafkaTemplate.send(eq("test.topic"), eq(eventId.toString()), eq("{\"key\":\"value\"}")))
                .thenReturn(CompletableFuture.completedFuture(sendResult));

        scheduler.processOutbox();

        verify(kafkaTemplate).send("test.topic", eventId.toString(), "{\"key\":\"value\"}");
        verify(outboxEventRepository).delete(event);
    }

    @Test
    void processOutbox_WhenKafkaFails_ShouldStillDeleteInThisDesign() {
        UUID eventId = UUID.randomUUID();
        OutboxEvent event = OutboxEvent.builder()
                .id(UUID.randomUUID())
                .aggregateId(eventId.toString())
                .topic("test.topic")
                .payload("{\"key\":\"value\"}")
                .build();

        when(outboxEventRepository.findAll(any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(event)));

        CompletableFuture<SendResult<String, Object>> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("Kafka error"));
        
        when(kafkaTemplate.send(anyString(), any(), any()))
                .thenReturn(failedFuture);

        scheduler.processOutbox();

        verify(kafkaTemplate).send("test.topic", eventId.toString(), "{\"key\":\"value\"}");
        // In the current implementation, delete happens in the same transaction regardless of Kafka ACK
        verify(outboxEventRepository).delete(event);
    }
}
