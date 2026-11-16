package org.portfolio.financeservice.config;

import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;
import tools.jackson.core.JacksonException;

@Configuration
public class KafkaErrorConfig {
    @Bean
    public DefaultErrorHandler deletionErrorHandler(KafkaTemplate<String, String> kafka, MeterRegistry metrics,
            @Value("${finance.kafka.retry-delay-ms:1000}") long delayMs) {
        if (delayMs < 1) {
            throw new IllegalArgumentException("Kafka retry delay must be positive");
        }
        var recoverer = new DeadLetterPublishingRecoverer(kafka,
                (record, error) -> new TopicPartition(record.topic() + ".finance.DLT", -1));
        recoverer.setFailIfSendResultIsError(true);
        var quarantined = metrics.counter("kafka.dead.letter.records");
        var handler = new DefaultErrorHandler((record, error) -> {
            recoverer.accept(record, error);
            quarantined.increment();
        }, new FixedBackOff(delayMs, FixedBackOff.UNLIMITED_ATTEMPTS));
        // Invalid JSON/contract cannot improve on retry; DB and acknowledgement failures can.
        handler.addNotRetryableExceptions(JacksonException.class, IllegalArgumentException.class);
        return handler;
    }
}
