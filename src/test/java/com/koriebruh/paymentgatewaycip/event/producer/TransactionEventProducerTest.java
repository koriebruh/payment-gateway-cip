package com.koriebruh.paymentgatewaycip.event.producer;

import com.koriebruh.paymentgatewaycip.event.model.TransactionSuccessEvent;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionEventProducerTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private TransactionEventProducer producer;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(producer, "topicCreated", "test.topic.created");
        ReflectionTestUtils.setField(producer, "topicFailed", "test.topic.failed");
    }

    @Test
    void publishSuccess_ShouldSendToCreatedTopic() {
        TransactionSuccessEvent event = new TransactionSuccessEvent(
                "tx-1", "ORD-1", "acc", java.math.BigDecimal.TEN, "IDR", "method", "channel",
                "CB-1", "BL-1", "SUCCESS", java.time.LocalDateTime.now(), "trace-1");
        
        ProducerRecord<String, Object> record = new ProducerRecord<>("test.topic.created", "ORD-1", event);
        RecordMetadata metadata = new RecordMetadata(new TopicPartition("test.topic.created", 0), 0L, 0, 0L, 0, 0);
        CompletableFuture<SendResult<String, Object>> future = CompletableFuture.completedFuture(new SendResult<>(record, metadata));
        
        when(kafkaTemplate.send(eq("test.topic.created"), eq("ORD-1"), eq(event))).thenReturn(future);

        producer.publishSuccess(event);

        verify(kafkaTemplate).send("test.topic.created", "ORD-1", event);
    }

    @Test
    void publishFailed_ShouldSendToFailedTopic() {
        TransactionSuccessEvent event = new TransactionSuccessEvent(
                "tx-1", "ORD-1", "acc", java.math.BigDecimal.TEN, "IDR", "method", "channel",
                null, null, "FAILED", java.time.LocalDateTime.now(), "trace-1");
        
        CompletableFuture<SendResult<String, Object>> future = new CompletableFuture<>();
        future.completeExceptionally(new RuntimeException("Kafka error"));
        
        when(kafkaTemplate.send(eq("test.topic.failed"), eq("ORD-1"), eq(event))).thenReturn(future);

        producer.publishFailed(event);

        verify(kafkaTemplate).send("test.topic.failed", "ORD-1", event);
    }
}
