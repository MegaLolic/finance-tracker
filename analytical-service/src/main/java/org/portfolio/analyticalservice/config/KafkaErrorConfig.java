package org.portfolio.analyticalservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.apache.kafka.common.TopicPartition;
import java.util.Map;

@Configuration
public class KafkaErrorConfig {
    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<?, ?> kafka,
            @Value("${analytics.kafka.retry-delay-ms:1000}") long delayMs,
            @Value("${analytics.kafka.retry-attempts:3}") long retries) {
        if (delayMs < 1 || retries < 0) {
            throw new IllegalArgumentException("Kafka retry delay must be positive and retries non-negative");
        }
        var recoverer = new DeadLetterPublishingRecoverer(kafka,
                (record, error) -> new TopicPartition(record.topic() + ".analytics.DLT", -1));
        // Never advance past the original record unless the broker accepted its DLT copy.
        recoverer.setFailIfSendResultIsError(true);
        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, new FixedBackOff(delayMs, retries));
        // Apply the same bounded policy to malformed JSON and listener failures.
        handler.setClassifications(Map.of(Exception.class, true), true);
        return handler;
    }
}
