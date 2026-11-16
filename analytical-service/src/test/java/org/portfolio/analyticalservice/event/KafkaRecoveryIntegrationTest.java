package org.portfolio.analyticalservice.event;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.portfolio.analyticalservice.config.KafkaErrorConfig;
import org.portfolio.analyticalservice.config.KafkaProducerConfig;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.kafka.listener.MessageListener;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Uses unique topics on an explicitly supplied test broker; removes them afterwards. */
@EnabledIfEnvironmentVariable(named = "KAFKA_TEST_BOOTSTRAP_SERVERS", matches = ".+")
class KafkaRecoveryIntegrationTest {
    @Test
    void malformedAndRejectedEventsReachDltAndFollowingEventStillRuns() throws Exception {
        String broker = System.getenv("KAFKA_TEST_BOOTSTRAP_SERVERS");
        String source = "recovery-test-" + UUID.randomUUID();
        String dlt = source + ".analytics.DLT";
        var properties = new KafkaProperties();
        properties.setBootstrapServers(List.of(broker));
        var producers = new KafkaProducerConfig().kafkaProducerFactory(properties);
        var template = new KafkaTemplate<>(producers);
        var processed = new CountDownLatch(1);
        Map<String, Object> config = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, broker,
                ConsumerConfig.GROUP_ID_CONFIG, source,
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        var consumers = new DefaultKafkaConsumerFactory<>(config, new StringDeserializer(),
                new ErrorHandlingDeserializer<>(new JacksonJsonDeserializer<>(TransactionEvent.class, false)));
        var containerProperties = new ContainerProperties(source);
        containerProperties.setAckMode(ContainerProperties.AckMode.RECORD);
        containerProperties.setMessageListener((MessageListener<String, TransactionEvent>) record -> {
            if ("reject".equals(record.value().description())) {
                throw new IllegalArgumentException("Test poison event");
            }
            processed.countDown();
        });
        var container = new KafkaMessageListenerContainer<>(consumers, containerProperties);
        container.setCommonErrorHandler(new KafkaErrorConfig().kafkaErrorHandler(template, 10, 2));
        try (var admin = AdminClient.create(Map.of("bootstrap.servers", broker));
             var deadLetters = new KafkaConsumer<>(config, new StringDeserializer(), new ByteArrayDeserializer())) {
            admin.createTopics(List.of(new NewTopic(source, 1, (short) 1), new NewTopic(dlt, 1, (short) 1)))
                    .all().get(20, TimeUnit.SECONDS);
            deadLetters.subscribe(List.of(dlt));
            container.start();
            byte[] malformed = "{invalid-json".getBytes(StandardCharsets.UTF_8);
            template.send(source, "1", malformed).get(10, TimeUnit.SECONDS);
            template.send(source, "1", event("reject")).get(10, TimeUnit.SECONDS);
            template.send(source, "1", event("valid")).get(10, TimeUnit.SECONDS);
            assertTrue(processed.await(30, TimeUnit.SECONDS), "Poison records must not block the valid event");
            var records = new ArrayList<org.apache.kafka.clients.consumer.ConsumerRecord<String, byte[]>>();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
            while (records.size() < 2 && System.nanoTime() < deadline) {
                deadLetters.poll(Duration.ofMillis(200)).forEach(records::add);
            }
            assertEquals(2, records.size());
            assertArrayEquals(malformed, records.get(0).value());
            String rejected = new String(records.get(1).value(), StandardCharsets.UTF_8);
            assertTrue(rejected.contains("\"description\":\"reject\""));
            assertNotNull(records.get(0).headers().lastHeader("kafka_dlt-original-topic"));
        } finally {
            container.stop();
            template.destroy();
            producers.reset();
            try (var admin = AdminClient.create(Map.of("bootstrap.servers", broker))) {
                admin.deleteTopics(List.of(source, dlt)).all().get(20, TimeUnit.SECONDS);
            }
        }
    }

    private TransactionEvent event(String description) {
        return new TransactionEvent(UUID.randomUUID(), "CREATED", 1L, 0L, 1L, 1L, 1L,
                java.math.BigDecimal.ONE, description, "EXPENSE", java.time.Instant.now());
    }
}
