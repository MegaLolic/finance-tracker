package org.portfolio.userservice;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.portfolio.userservice.deletion.*;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.*;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** Creates unique topics only on a broker explicitly selected for integration testing. */
@EnabledIfEnvironmentVariable(named = "KAFKA_TEST_BOOTSTRAP_SERVERS", matches = ".+")
class UserDeletionKafkaIntegrationTest {
    @Test
    void poisonAcknowledgementsAreQuarantinedWhileTemporaryDatabaseFailuresKeepRetrying() throws Exception {
        String broker = System.getenv("KAFKA_TEST_BOOTSTRAP_SERVERS");
        String source = "user-deletion-test-" + UUID.randomUUID();
        String dlt = source + ".user.DLT";
        var producers = new DefaultKafkaProducerFactory<>(Map.of("bootstrap.servers", broker),
                new StringSerializer(), new StringSerializer());
        var template = new KafkaTemplate<>(producers);
        var metrics = new SimpleMeterRegistry();
        var processed = new CountDownLatch(1);
        var attempts = new AtomicInteger();
        var service = new UserDeletionService(null) {
            @Override
            public void acknowledge(UserDeletionEvent event) {
                if (!"finance".equals(event.service())) {
                    throw new IllegalArgumentException("Unknown deletion participant");
                }
                if (attempts.incrementAndGet() <= 4) {
                    throw new DataAccessResourceFailureException("Temporary database outage");
                }
                processed.countDown();
            }
        };
        var relay = new UserDeletionRelay(null, template, JsonMapper.builder().build(), service);
        Map<String, Object> config = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, broker,
                ConsumerConfig.GROUP_ID_CONFIG, source,
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        var consumers = new DefaultKafkaConsumerFactory<>(config, new StringDeserializer(), new StringDeserializer());
        var properties = new ContainerProperties(source);
        properties.setAckMode(ContainerProperties.AckMode.RECORD);
        properties.setMessageListener((MessageListener<String, String>) record -> relay.acknowledge(record.value()));
        var container = new KafkaMessageListenerContainer<>(consumers, properties);
        container.setCommonErrorHandler(new UserDeletionConfig().deletionErrorHandler(template, metrics, 10, 2));
        try (var admin = AdminClient.create(Map.of("bootstrap.servers", broker));
             var deadLetters = new KafkaConsumer<>(config, new StringDeserializer(), new StringDeserializer())) {
            admin.createTopics(List.of(new NewTopic(source, 1, (short) 1), new NewTopic(dlt, 1, (short) 1)))
                    .all().get(20, TimeUnit.SECONDS);
            deadLetters.subscribe(List.of(dlt));
            container.start();
            String malformed = "{invalid-json";
            String invalidParticipant = "{\"requestId\":\"" + UUID.randomUUID()
                    + "\",\"userId\":1,\"service\":\"unknown\"}";
            String valid = invalidParticipant.replace("unknown", "finance");
            template.send(source, "1", malformed).get(10, TimeUnit.SECONDS);
            template.send(source, "1", invalidParticipant).get(10, TimeUnit.SECONDS);
            template.send(source, "1", valid).get(10, TimeUnit.SECONDS);
            assertTrue(processed.await(30, TimeUnit.SECONDS));
            var values = new ArrayList<String>();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
            while (values.size() < 2 && System.nanoTime() < deadline) {
                deadLetters.poll(Duration.ofMillis(200)).forEach(record -> values.add(record.value()));
            }
            assertEquals(List.of(malformed, invalidParticipant), values);
            assertEquals(5, attempts.get(), "Temporary failures must retry beyond the invalid-message limit");
            assertEquals(2.0, metrics.get("kafka.dead.letter.records").counter().count());
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
