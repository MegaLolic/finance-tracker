package org.portfolio.userservice.deletion;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.util.backoff.FixedBackOff;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import tools.jackson.core.JacksonException;

import java.util.Map;
import io.micrometer.core.instrument.MeterRegistry;

@Configuration
@EnableScheduling
public class UserDeletionConfig {
    @Bean
    public NewTopic userDeletionRequests(org.springframework.kafka.core.KafkaAdmin admin) {
        admin.setModifyTopicConfigs(true);
        return TopicBuilder.name("user-deletion-requests").partitions(3).replicas(1)
                .config(org.apache.kafka.common.config.TopicConfig.CLEANUP_POLICY_CONFIG, "compact").build();
    }

    @Bean
    public NewTopic userDeletionCompleted() {
        return TopicBuilder.name("user-deletion-completed").partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic userDeletionDeadLetters() {
        return TopicBuilder.name("user-deletion-completed.user.DLT").partitions(3).replicas(1)
                .config(org.apache.kafka.common.config.TopicConfig.RETENTION_MS_CONFIG, "1209600000")
                .config(org.apache.kafka.common.config.TopicConfig.RETENTION_BYTES_CONFIG, "-1").build();
    }

    @Bean
    public DefaultErrorHandler deletionErrorHandler(KafkaTemplate<?, ?> kafka, MeterRegistry metrics,
            @Value("${user-deletion.retry-delay-ms:1000}") long delayMs,
            @Value("${user-deletion.invalid-message-retries:3}") long retries) {
        if (delayMs < 1 || retries < 0) {
            throw new IllegalArgumentException("Deletion retry delay must be positive and retries non-negative");
        }
        var recoverer = new DeadLetterPublishingRecoverer(kafka,
                (record, error) -> new TopicPartition(record.topic() + ".user.DLT", -1));
        recoverer.setFailIfSendResultIsError(true);
        var quarantined = metrics.counter("kafka.dead.letter.records");
        var handler = new DefaultErrorHandler((record, failure) -> {
            recoverer.accept(record, failure);
            quarantined.increment();
        },
                new FixedBackOff(delayMs, FixedBackOff.UNLIMITED_ATTEMPTS));
        handler.setClassifications(Map.of(Exception.class, true), true);
        handler.setBackOffFunction((record, failure) -> new FixedBackOff(delayMs,
                invalidMessage(failure) ? retries : FixedBackOff.UNLIMITED_ATTEMPTS));
        return handler;
    }

    private boolean invalidMessage(Throwable failure) {
        while (failure != null) {
            if (failure instanceof IllegalArgumentException || failure instanceof JacksonException) {
                return true;
            }
            failure = failure.getCause();
        }
        return false;
    }
}
