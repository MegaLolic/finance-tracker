package org.portfolio.analyticalservice;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.portfolio.analyticalservice.document.RawTransaction;
import org.portfolio.analyticalservice.repository.RawTransactionRepository;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.repository.support.MongoRepositoryFactory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@EnabledIfEnvironmentVariable(named = "ANALYTICS_TEST_MONGO_URI", matches = ".+")
class AnalyticsRepositoryIntegrationTest {
    private static MongoClient client;
    private static MongoTemplate mongo;

    @BeforeAll
    static void setup() {
        client = MongoClients.create(System.getenv("ANALYTICS_TEST_MONGO_URI"));
        mongo = new MongoTemplate(client, "analytics_test_" + UUID.randomUUID().toString().replace("-", ""));
    }

    @AfterAll
    static void cleanup() {
        if (mongo != null) { mongo.getDb().drop(); }
        if (client != null) { client.close(); }
    }

    @Test
    void queryIsolatesOwnerAccountAndActiveTransactionsWithHalfOpenTimeBounds() {
        Instant from = Instant.parse("2026-11-01T00:00:00Z");
        Instant to = Instant.parse("2026-12-01T00:00:00Z");
        mongo.save(transaction(1L, 7L, 3L, false, from));
        mongo.save(transaction(2L, 7L, 3L, false, to.minusMillis(1)));
        mongo.save(transaction(3L, 7L, 3L, false, from.minusMillis(1)));
        mongo.save(transaction(4L, 7L, 3L, false, to));
        mongo.save(transaction(5L, 8L, 3L, false, from));
        mongo.save(transaction(6L, 7L, 4L, false, from));
        mongo.save(transaction(7L, 7L, 3L, true, from));
        var repository = new MongoRepositoryFactory(mongo).getRepository(RawTransactionRepository.class);

        var result = repository
                .findAllByUserIdAndAccountIdAndDeletedFalseAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(7L, 3L, from, to);

        assertEquals(java.util.List.of(1L, 2L), result.stream().map(RawTransaction::getId).sorted().toList());
        assertEquals(new BigDecimal("10.01"), result.getFirst().getAmount());
    }

    private RawTransaction transaction(Long id, Long userId, Long accountId, boolean deleted, Instant createdAt) {
        return RawTransaction.builder().id(id).userId(userId).accountId(accountId).deleted(deleted)
                .categoryId(1L).categoryType("INCOME").amount(new BigDecimal("10.01")).createdAt(createdAt).build();
    }
}
