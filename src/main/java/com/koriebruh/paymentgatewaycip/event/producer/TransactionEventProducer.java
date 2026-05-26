package com.koriebruh.paymentgatewaycip.event.producer;

import com.koriebruh.paymentgatewaycip.event.model.TransactionSuccessEvent;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * Kafka producer for transaction domain events.
 * Publishes async — never blocks the HTTP thread.
 */
@Component
@RequiredArgsConstructor
public class TransactionEventProducer {

    private static final Logger log = LoggerFactory.getLogger(TransactionEventProducer.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${app.kafka.topics.transaction-created}")
    private String topicCreated;

    @Value("${app.kafka.topics.transaction-failed}")
    private String topicFailed;

    public void publishSuccess(TransactionSuccessEvent event) {
        send(topicCreated, event.orderId(), event);
    }

    public void publishFailed(TransactionSuccessEvent event) {
        send(topicFailed, event.orderId(), event);
    }

    private void send(String topic, String key, Object payload) {
        CompletableFuture<SendResult<String, Object>> future =
                kafkaTemplate.send(topic, key, payload);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("[Kafka] Failed — topic={} key={} err={}", topic, key, ex.getMessage(), ex);
            } else {
                log.info("[Kafka] Sent — topic={} partition={} offset={}",
                        topic,
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            }
        });
    }
}
