package com.koriebruh.paymentgatewaycip.event.scheduler;

import com.koriebruh.paymentgatewaycip.entity.OutboxEvent;
import com.koriebruh.paymentgatewaycip.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
public class TransactionOutboxScheduler {

    private static final Logger log = LoggerFactory.getLogger(TransactionOutboxScheduler.class);

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Scheduled(fixedDelayString = "${app.outbox.poll-rate:5000}")
    @Transactional
    public void processOutbox() {
        List<OutboxEvent> events = outboxEventRepository.findAll(PageRequest.of(0, 100)).getContent();
        if (events.isEmpty()) {
            return;
        }

        log.info("Processing {} outbox events...", events.size());

        for (OutboxEvent event : events) {
            CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(event.getTopic(), event.getAggregateId(), event.getPayload());

            future.whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("[Outbox] Failed to send to Kafka — topic={} key={} err={}",
                            event.getTopic(), event.getAggregateId(), ex.getMessage(), ex);
                } else {
                    log.info("[Outbox] Sent to Kafka — topic={} partition={} offset={}",
                            event.getTopic(),
                            result.getRecordMetadata().partition(),
                            result.getRecordMetadata().offset());
                }
            });

            // Delete the event from outbox table
            // In a real production system, you might want to wait for Kafka ACK before deleting,
            // but for simplicity in this scheduler we delete it in the same DB transaction.
            outboxEventRepository.delete(event);
        }
    }
}
