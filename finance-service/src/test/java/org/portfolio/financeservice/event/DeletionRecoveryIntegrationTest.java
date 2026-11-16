package org.portfolio.financeservice.event;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.portfolio.financeservice.config.KafkaErrorConfig;
import org.portfolio.financeservice.deletion.UserDeletionEvent;
import org.portfolio.financeservice.deletion.UserDeletionListener;
import org.portfolio.financeservice.deletion.UserDeletionService;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.kafka.listener.MessageListener;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named = "KAFKA_TEST_BOOTSTRAP_SERVERS", matches = ".+")
class DeletionRecoveryIntegrationTest {
    @Test
    void malformedRequestIsQuarantinedButDatabaseFailuresRetry() throws Exception {
        String broker = System.getenv("KAFKA_TEST_BOOTSTRAP_SERVERS");
        String source = "finance-deletion-test-" + UUID.randomUUID();
        String dlt = source + ".finance.DLT";
        var producers = new DefaultKafkaProducerFactory<>(Map.<String, Object>of("bootstrap.servers", broker),
                new StringSerializer(), new StringSerializer());
        var template = new KafkaTemplate<>(producers);
        var metrics = new SimpleMeterRegistry();
        var service = mock(UserDeletionService.class);
        doThrow(new DataAccessResourceFailureException("Temporary database outage"))
                .doNothing().when(service).delete(987654321L);
        var mapper = JsonMapper.builder().build();
        var listener = new UserDeletionListener(service, mapper, template);
        var completed = new CountDownLatch(1);
        Map<String, Object> config = Map.of(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, broker,
                ConsumerConfig.GROUP_ID_CONFIG, source, ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        var consumers = new DefaultKafkaConsumerFactory<>(config, new StringDeserializer(), new StringDeserializer());
        var containerProperties = new ContainerProperties(source);
        containerProperties.setAckMode(ContainerProperties.AckMode.RECORD);
        containerProperties.setMessageListener((MessageListener<String, String>) record -> {
            try {
                listener.delete(record.value());
                completed.countDown();
            } catch (RuntimeException failure) {
                throw failure;
            } catch (Exception failure) {
                throw new IllegalStateException(failure);
            }
        });
        var container = new KafkaMessageListenerContainer<>(consumers, containerProperties);
        container.setCommonErrorHandler(new KafkaErrorConfig().deletionErrorHandler(template, metrics, 10));
        try (var admin = AdminClient.create(Map.of("bootstrap.servers", broker));
             var deadLetters = new KafkaConsumer<>(config, new StringDeserializer(), new StringDeserializer())) {
            var topics = new java.util.ArrayList<>(List.of(
                    new NewTopic(source, 1, (short) 1), new NewTopic(dlt, 1, (short) 1)));
            if (!admin.listTopics().names().get(10, TimeUnit.SECONDS).contains("user-deletion-completed")) {
                topics.add(new NewTopic("user-deletion-completed", 3, (short) 1));
            }
            admin.createTopics(topics).all().get(20, TimeUnit.SECONDS);
            deadLetters.subscribe(List.of(dlt));
            container.start();
            template.send(source, "987654321", "null").get(10, TimeUnit.SECONDS);
            template.send(source, "987654321", "{invalid-json").get(10, TimeUnit.SECONDS);
            template.send(source, "987654321", mapper.writeValueAsString(
                    new UserDeletionEvent(UUID.randomUUID(), 987654321L, null))).get(10, TimeUnit.SECONDS);
            assertTrue(completed.await(30, TimeUnit.SECONDS));
            var records = new java.util.ArrayList<String>();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
            while (records.size() < 2 && System.nanoTime() < deadline) {
                deadLetters.poll(Duration.ofMillis(200)).forEach(record -> records.add(record.value()));
            }
            assertEquals(List.of("null", "{invalid-json"), records);
            verify(service, times(2)).delete(987654321L);
            assertEquals(2, metrics.get("kafka.dead.letter.records").counter().count());
        } finally {
            container.stop();
            template.destroy();
            producers.reset();
            metrics.close();
            try (var admin = AdminClient.create(Map.of("bootstrap.servers", broker))) {
                admin.deleteTopics(List.of(source, dlt)).all().get(20, TimeUnit.SECONDS);
            }
        }
    }
}
