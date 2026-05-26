package com.koriebruh.paymentgatewaycip.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/**
 * Typed configuration for all custom "app.*" properties.
 * <p>
 * Registers metadata so IntelliJ / IDE can resolve and auto-complete
 * {@code app.kafka.topics.*} entries in application.yaml.
 * </p>
 *
 * <pre>
 * app:
 *   kafka:
 *     topics:
 *       transaction-created: payment.transaction.created
 *       transaction-updated: payment.transaction.updated
 *       transaction-failed:  payment.transaction.failed
 * </pre>
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private Kafka kafka = new Kafka();

    @Getter
    @Setter
    public static class Kafka {

        /**
         * Map of logical topic key → actual Kafka topic name.
         * Keys: transaction-created, transaction-updated, transaction-failed
         */
        private Topics topics = new Topics();

        @Getter
        @Setter
        public static class Topics {
            private String transactionCreated = "payment.transaction.created";
            private String transactionUpdated  = "payment.transaction.updated";
            private String transactionFailed   = "payment.transaction.failed";
        }
    }
}
