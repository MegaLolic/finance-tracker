package org.portfolio.analyticalservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;
import java.util.Map;

@Configuration
public class KafkaErrorConfig {
    @Bean
    public DefaultErrorHandler kafkaErrorHandler() {
        FixedBackOff retryPolicy = new FixedBackOff(1000L, FixedBackOff.UNLIMITED_ATTEMPTS);
        DefaultErrorHandler handler = new DefaultErrorHandler(retryPolicy);
        // Do not silently discard an event after the default retry limit, including conversion failures.
        handler.setClassifications(Map.of(Exception.class, true), true);
        return handler;
    }
}
