package org.portfolio.financeservice.event;

import jakarta.persistence.EntityManagerFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.portfolio.financeservice.client.UserClient;
import org.portfolio.financeservice.dto.TransactionDto;
import org.portfolio.financeservice.repository.*;
import org.portfolio.financeservice.service.AccountService;
import org.portfolio.financeservice.service.TransactionService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named = "FINANCE_TEST_DB_URL", matches = ".+")
class OutboxIntegrationTest {
    private static final String SCHEMA = "outbox_test_" + UUID.randomUUID().toString().replace("-", "");
    private static DriverManagerDataSource source;
    private static JdbcTemplate jdbc;
    private static LocalContainerEntityManagerFactoryBean factory;
    private static EntityManagerFactory emf;
    private static TransactionTemplate tx;
    private static String migrationSnapshot;
    private final JsonMapper mapper = JsonMapper.builder().build();
    private KafkaTemplate<String, String> kafka;
    private OutboxPublisher publisher;
    private AccountService accounts;
    private TransactionService operations;

    @BeforeAll
    static void initialize() {
        String url = System.getenv("FINANCE_TEST_DB_URL");
        String user = System.getenv().getOrDefault("FINANCE_TEST_DB_USER", "postgres");
        String password = System.getenv().getOrDefault("FINANCE_TEST_DB_PASSWORD", "");
        Flyway.configure().dataSource(url, user, password).schemas(SCHEMA).defaultSchema(SCHEMA)
                .target("3").load().migrate();
        source = new DriverManagerDataSource(url + (url.contains("?") ? "&" : "?") + "currentSchema=" + SCHEMA, user, password);
        jdbc = new JdbcTemplate(source);
        jdbc.update("INSERT INTO accounts(user_id,name,balance,currency) VALUES (1,'Existing',100,'BYN')");
        jdbc.update("INSERT INTO categories(user_id,name,type) VALUES (1,'Existing','EXPENSE')");
        jdbc.update("INSERT INTO transactions(user_id,account_id,category_id,amount) VALUES (1,1,1,10)");
        Flyway.configure().dataSource(url, user, password).schemas(SCHEMA).defaultSchema(SCHEMA).load().migrate();
        migrationSnapshot = jdbc.queryForObject("SELECT payload::text FROM outbox_events", String.class);
        factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(source);
        factory.setPackagesToScan("org.portfolio.financeservice.entity");
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "validate",
                "hibernate.physical_naming_strategy", "org.hibernate.boot.model.naming.PhysicalNamingStrategySnakeCaseImpl"));
        factory.afterPropertiesSet();
        emf = Objects.requireNonNull(factory.getObject());
        var manager = new JpaTransactionManager(emf);
        manager.setDataSource(source);
        tx = new TransactionTemplate(manager);
    }

    @AfterAll
    static void cleanup() {
        if (factory != null) { factory.destroy(); }
        if (jdbc != null) { jdbc.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE"); }
    }

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setup() {
        jdbc.execute("TRUNCATE outbox_events, transactions, categories, accounts RESTART IDENTITY CASCADE");
        jdbc.update("INSERT INTO accounts(user_id,name,balance,currency) VALUES (1,'Test',100,'BYN')");
        jdbc.update("INSERT INTO categories(user_id,name,type) VALUES (1,'Test','EXPENSE')");
        var repositoryFactory = new JpaRepositoryFactory(SharedEntityManagerCreator.createSharedEntityManager(emf));
        var accountRepository = repositoryFactory.getRepository(AccountRepository.class);
        var categoryRepository = repositoryFactory.getRepository(CategoryRepository.class);
        var transactionRepository = repositoryFactory.getRepository(TransactionRepository.class);
        var userClient = mock(UserClient.class);
        var outbox = new OutboxService(jdbc, mapper);
        accounts = new AccountService(accountRepository, userClient, transactionRepository, categoryRepository, outbox);
        operations = new TransactionService(transactionRepository, accountRepository, categoryRepository, userClient, accounts, outbox);
        kafka = mock(KafkaTemplate.class);
        when(kafka.send(anyString(), anyString(), anyString())).thenReturn(CompletableFuture.completedFuture(null));
        publisher = new OutboxPublisher(jdbc, kafka, tx, "test-events", 20, 1000, 60000);
    }

    private TransactionDto input(String amount, Long version) {
        return TransactionDto.builder().accountId(1L).categoryId(1L).amount(new BigDecimal(amount))
                .description("Test").version(version).build();
    }

    private TransactionDto create(String amount) {
        return tx.execute(status -> operations.createTransaction(1L, input(amount, null)));
    }

    private List<TransactionEvent> events() {
        return jdbc.query("SELECT payload::text FROM outbox_events ORDER BY sequence",
                (rs, row) -> mapper.readValue(rs.getString(1), TransactionEvent.class));
    }

    @Test
    void migrationEnqueuesExistingTransactions() {
        TransactionEvent snapshot = mapper.readValue(migrationSnapshot, TransactionEvent.class);
        assertEquals("UPDATED", snapshot.eventType());
        assertEquals(0L, snapshot.version());
        assertEquals(1L, snapshot.transactionId());
        assertNotNull(snapshot.createdAt());
    }

    @Test
    void createUpdateDeleteStoreSnapshotsAndNeverSendBeforeCommit() {
        var created = create("10.25");
        tx.executeWithoutResult(status -> operations.updateTransaction(created.getId(), 1L, input("20.50", 0L)));
        tx.executeWithoutResult(status -> operations.deleteTransaction(created.getId(), 1L, 1L));
        var events = events();
        assertEquals(List.of("CREATED", "UPDATED", "DELETED"), events.stream().map(TransactionEvent::eventType).toList());
        assertEquals(List.of(0L, 1L, 2L), events.stream().map(TransactionEvent::version).toList());
        assertEquals(new BigDecimal("20.50"), events.get(1).amount());
        assertEquals(new BigDecimal("100.00"), jdbc.queryForObject("SELECT balance FROM accounts WHERE id=1", BigDecimal.class));
        verifyNoInteractions(kafka);
    }

    @Test
    void rollbackRemovesBothOperationAndOutboxRecord() {
        assertThrows(IllegalStateException.class, () -> tx.executeWithoutResult(status -> {
            operations.createTransaction(1L, input("10", null));
            throw new IllegalStateException("Simulated failure after writing the outbox");
        }));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM transactions", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM outbox_events", Integer.class));
        assertEquals(new BigDecimal("100.00"), jdbc.queryForObject("SELECT balance FROM accounts WHERE id=1", BigDecimal.class));
    }

    @Test
    void kafkaFailureRetriesOldestEventBeforeLaterVersionsWithoutBlockingOtherTransactions() {
        var first = create("10");
        tx.executeWithoutResult(status -> operations.updateTransaction(first.getId(), 1L, input("20", 0L)));
        var second = create("5");
        when(kafka.send(eq("test-events"), eq(first.getId().toString()), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("Kafka down")));
        publisher.publishPending();
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM outbox_events WHERE published_at IS NOT NULL", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT attempts FROM outbox_events WHERE transaction_id=? ORDER BY sequence LIMIT 1",
                Integer.class, first.getId()));
        assertEquals(0, jdbc.queryForObject("SELECT attempts FROM outbox_events WHERE event_type='UPDATED'", Integer.class));
        verify(kafka).send(eq("test-events"), eq(second.getId().toString()), anyString());

        when(kafka.send(eq("test-events"), eq(first.getId().toString()), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));
        publisher.publishPending(); // Retry delay prevents a hot retry loop.
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM outbox_events WHERE published_at IS NOT NULL", Integer.class));
        jdbc.update("UPDATE outbox_events SET next_attempt_at=CURRENT_TIMESTAMP-INTERVAL '1 second'");
        publisher.publishPending();
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM outbox_events WHERE published_at IS NOT NULL", Integer.class));
        publisher.publishPending();
        assertEquals(3, jdbc.queryForObject("SELECT count(*) FROM outbox_events WHERE published_at IS NOT NULL", Integer.class));
    }

    @Test
    void accountDeletionEnqueuesEveryCascadedTransaction() {
        create("10");
        create("20");
        Long version = jdbc.queryForObject("SELECT version FROM accounts WHERE id=1", Long.class);
        tx.executeWithoutResult(status -> accounts.deleteAccount(1L, 1L, version));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM transactions", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM accounts", Integer.class));
        assertEquals(2, events().stream().filter(e -> e.eventType().equals("DELETED")).count());
        assertTrue(events().stream().filter(e -> e.eventType().equals("DELETED")).allMatch(e -> e.version() == 1L));
    }

    @Test
    void cleanupOnlyRemovesExpiredPublishedEventsInBoundedBatches() {
        create("10");
        create("20");
        create("30");
        create("40");
        jdbc.update("""
                UPDATE outbox_events SET published_at = CASE
                    WHEN sequence IN (SELECT sequence FROM outbox_events ORDER BY sequence LIMIT 2)
                    THEN CURRENT_TIMESTAMP - INTERVAL '8 days'
                    ELSE CURRENT_TIMESTAMP END
                """);
        jdbc.update("""
                UPDATE outbox_events SET published_at = NULL, created_at = CURRENT_TIMESTAMP - INTERVAL '30 days'
                WHERE sequence = (SELECT max(sequence) FROM outbox_events)
                """);
        var cleanup = new OutboxCleanup(jdbc, 7, 1);
        cleanup.cleanPublished();
        assertEquals(3, jdbc.queryForObject("SELECT count(*) FROM outbox_events", Integer.class));
        cleanup.cleanPublished();
        cleanup.cleanPublished();
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM outbox_events", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM outbox_events WHERE published_at IS NULL", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM outbox_events WHERE published_at IS NOT NULL", Integer.class));
    }

    @Test
    void twoRelaysDoNotSendTheSamePendingBatchConcurrently() throws Exception {
        create("10");
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        when(kafka.send(anyString(), anyString(), anyString())).thenAnswer(invocation -> {
            entered.countDown();
            if (!release.await(5, TimeUnit.SECONDS)) { throw new IllegalStateException("Test timeout"); }
            return CompletableFuture.completedFuture(null);
        });
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(publisher::publishPending);
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            try {
                executor.submit(publisher::publishPending).get(3, TimeUnit.SECONDS);
                verify(kafka, times(1)).send(anyString(), anyString(), anyString());
            } finally { release.countDown(); }
            first.get(5, TimeUnit.SECONDS);
        }
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM outbox_events WHERE published_at IS NOT NULL", Integer.class));
    }
}
