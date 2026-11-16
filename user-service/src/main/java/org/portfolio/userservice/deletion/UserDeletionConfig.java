package org.portfolio.userservice.deletion;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.util.backoff.FixedBackOff;

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
    public DefaultErrorHandler deletionErrorHandler() {
        return new DefaultErrorHandler(new FixedBackOff(1000L, FixedBackOff.UNLIMITED_ATTEMPTS));
    }
}
