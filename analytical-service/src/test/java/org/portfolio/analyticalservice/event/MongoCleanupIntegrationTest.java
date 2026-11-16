package org.portfolio.analyticalservice.event;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.bson.Document;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.portfolio.analyticalservice.config.MongoIndexConfig;
import org.portfolio.analyticalservice.deletion.DeletedUserCleanup;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "ANALYTICS_TEST_MONGO_URI", matches = ".+")
class MongoCleanupIntegrationTest {
    private static MongoClient client;
    private static MongoTemplate mongo;

    @BeforeAll
    static void setup() {
        client = MongoClients.create(System.getenv("ANALYTICS_TEST_MONGO_URI"));
        mongo = new MongoTemplate(client, "cleanup_test_" + UUID.randomUUID().toString().replace("-", ""));
    }

    @AfterAll
    static void cleanup() {
        if (mongo != null) { mongo.getDb().drop(); }
        if (client != null) { client.close(); }
    }

    @Test
    void indexIsIdempotentAndCleanupCyclesThroughBoundedBatches() {
        var indexes = new MongoIndexConfig(mongo);
        indexes.createIndexes();
        indexes.createIndexes();
        var data = mongo.getCollection("raw_transactions");
        assertNotNull(data.listIndexes().into(new java.util.ArrayList<>()).stream()
                .filter(index -> new Document("userId", 1).equals(index.get("key"))).findFirst().orElse(null));
        for (long userId = 1; userId <= 3; userId++) {
            mongo.getCollection("deleted_users").insertOne(new Document("_id", userId));
            data.insertOne(new Document("_id", userId).append("userId", userId));
        }
        data.insertOne(new Document("_id", 4L).append("userId", 4L));
        var cleanup = new DeletedUserCleanup(mongo, 2);
        cleanup.cleanup();
        assertEquals(2, data.countDocuments());
        cleanup.cleanup();
        assertEquals(1, data.countDocuments());
        assertNotNull(data.find(new Document("_id", 4L)).first());
        // Simulate a late write after a crash. The next cycle must revisit early tombstones.
        data.insertOne(new Document("_id", 5L).append("userId", 1L));
        cleanup.cleanup();
        assertEquals(1, data.countDocuments());
        assertEquals(3, mongo.getCollection("deleted_users").countDocuments());
    }
}
