package org.portfolio.analyticalservice.config;

import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.portfolio.analyticalservice.event.TransactionEvent;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.DelegatingByTypeSerializer;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;

import java.util.Map;

@Configuration
public class KafkaProducerConfig {
    @Bean
    public ProducerFactory<Object, Object> kafkaProducerFactory(KafkaProperties properties) {
        // Deletion acknowledgements remain strings; DLT also needs events and invalid JSON bytes.
        var values = new DelegatingByTypeSerializer(Map.of(
                String.class, new StringSerializer(),
                byte[].class, new ByteArraySerializer(),
                TransactionEvent.class, new JacksonJsonSerializer<TransactionEvent>()));
        return new DefaultKafkaProducerFactory<>(properties.buildProducerProperties(),
                new DelegatingByTypeSerializer(Map.of(String.class, new StringSerializer(),
                        byte[].class, new ByteArraySerializer())), values);
    }
}
