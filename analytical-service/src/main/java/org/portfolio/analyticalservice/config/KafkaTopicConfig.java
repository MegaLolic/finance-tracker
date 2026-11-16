package org.portfolio.analyticalservice.config;

import org.apache.kafka.common.config.TopicConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;

@Configuration
public class KafkaTopicConfig {
    @Bean
    public KafkaAdmin.NewTopics analyticsDeadLetterTopics(@Value("${topic.name}") String topic) {
        return new KafkaAdmin.NewTopics(deadLetters(topic), deadLetters("user-deletion-requests"));
    }

    private org.apache.kafka.clients.admin.NewTopic deadLetters(String source) {
        return TopicBuilder.name(source + ".analytics.DLT").partitions(3).replicas(1)
                .config(TopicConfig.RETENTION_MS_CONFIG, "1209600000")
                .config(TopicConfig.RETENTION_BYTES_CONFIG, "-1").build();
    }
}
