package org.portfolio.financeservice.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.TopicConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;

@Configuration
public class KafkaConfig {
    @Bean
    public NewTopic financeDeletionDeadLetters() {
        return TopicBuilder.name("user-deletion-requests.finance.DLT").partitions(3).replicas(1)
                .config(TopicConfig.RETENTION_MS_CONFIG, "1209600000")
                .config(TopicConfig.RETENTION_BYTES_CONFIG, "-1").build();
    }

    @Bean
    public NewTopic financePlacedTopic(@Value("${topic.name}") String topicName, KafkaAdmin admin){
        admin.setModifyTopicConfigs(true);
        return TopicBuilder.name(topicName)
                .partitions(3)
                .replicas(1)
                .config(TopicConfig.RETENTION_MS_CONFIG,"604800000")
                .config(TopicConfig.RETENTION_BYTES_CONFIG,"-1")
                .build();
    }
}
