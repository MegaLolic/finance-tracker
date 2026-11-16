package org.portfolio.analyticalservice.event;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.bson.Document;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.portfolio.analyticalservice.document.RawTransaction;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "ANALYTICS_TEST_MONGO_URI", matches = ".+")
class TransactionEventListenerTest {
    private static MongoClient client;
    private static MongoTemplate mongo;
    private static TransactionEventListener listener;

    @BeforeAll
    static void setup() {
        client = MongoClients.create(System.getenv("ANALYTICS_TEST_MONGO_URI"));
        mongo = new MongoTemplate(client, "outbox_test_" + UUID.randomUUID().toString().replace("-", ""));
        listener = new TransactionEventListener(mongo);
    }

    @AfterAll
    static void cleanup() {
        if (mongo != null) { mongo.getDb().drop(); }
        if (client != null) { client.close(); }
    }

    @BeforeEach
    void clear() { mongo.getCollection("raw_transactions").deleteMany(new Document()); }

    private TransactionEvent event(String type, long version, String amount) {
        return new TransactionEvent(UUID.randomUUID(), type, 1L, version, 2L, 3L, 4L,
                new BigDecimal(amount), "$literal description", "EXPENSE", Instant.EPOCH);
    }

    private RawTransaction state() { return mongo.findById(1L, RawTransaction.class); }

    @Test
    void updatesReplaceDataAndDuplicatesDoNotCreateExtraDocuments() {
        listener.onTransactionEvent(event("CREATED", 0, "10"));
        var update = event("UPDATED", 1, "20");
        listener.onTransactionEvent(update);
        listener.onTransactionEvent(update);
        assertEquals(1, mongo.getCollection("raw_transactions").countDocuments());
        assertEquals(1L, state().getVersion());
        assertEquals(new BigDecimal("20"), state().getAmount());
        assertEquals("$literal description", state().getDescription());
    }

    @Test
    void lateEventsCannotResurrectDeletedTransactions() {
        listener.onTransactionEvent(event("DELETED", 2, "20"));
        listener.onTransactionEvent(event("UPDATED", 1, "20"));
        listener.onTransactionEvent(event("CREATED", 0, "10"));
        assertTrue(state().isDeleted());
        assertEquals(2L, state().getVersion());
    }

    @Test
    void snapshotUpgradesLegacyDocumentsAndLegacyEventsCannotOverwriteNewState() {
        mongo.getCollection("raw_transactions").insertOne(new Document("_id", 1L).append("amount", "5"));
        listener.onTransactionEvent(event("UPDATED", 0, "20"));
        listener.onTransactionEvent(new TransactionEvent(null, null, 1L, null, 2L, 3L, 4L,
                BigDecimal.TEN, "Legacy", "EXPENSE", Instant.EPOCH));
        assertEquals(0L, state().getVersion());
        assertEquals(new BigDecimal("20"), state().getAmount());
    }

    @Test
    void concurrentOutOfOrderEventsKeepTheHighestVersion() throws Exception {
        // Pre-create the ID to focus on atomic version comparison, not initial upsert collisions.
        listener.onTransactionEvent(event("CREATED", 0, "1"));
        try (var executor = Executors.newFixedThreadPool(4)) {
            var futures = IntStream.rangeClosed(1, 15).mapToObj(version ->
                    executor.submit(() -> listener.onTransactionEvent(event("UPDATED", version, "" + version)))).toList();
            for (var future : futures) { future.get(); }
        }
        assertEquals(15L, state().getVersion());
        assertEquals(new BigDecimal("15"), state().getAmount());
    }

    @Test
    void jsonDeserializerAcceptsTheOutboxWireFormat() {
        String json = """
                {"eventId":"7b611742-3afd-4ea5-b4cb-3419ce572c87","eventType":"UPDATED",
                "transactionId":1,"version":3,"userId":2,"accountId":3,"categoryId":4,
                "amount":12.25,"description":"Test","categoryType":"EXPENSE","createdAt":"2026-11-12T12:00:00Z"}
                """;
        try (var deserializer = new JacksonJsonDeserializer<>(TransactionEvent.class, false)) {
            var event = deserializer.deserialize("test-events", json.getBytes(StandardCharsets.UTF_8));
            listener.onTransactionEvent(event);
            assertEquals(3L, state().getVersion());
            assertEquals(new BigDecimal("12.25"), state().getAmount());
        }
    }
}
