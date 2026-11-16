package org.portfolio.userservice;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.portfolio.userservice.deletion.UserDeletionEvent;
import org.portfolio.userservice.deletion.UserDeletionService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Uses an isolated schema; never deletes rows in the application's public schema. */
@EnabledIfEnvironmentVariable(named = "USER_TEST_DB_URL", matches = ".+")
class UserDeletionIntegrationTest {
    private static final String SCHEMA = "deletion_test_" + UUID.randomUUID().toString().replace("-", "");
    private static JdbcTemplate jdbc;
    private static TransactionTemplate transactions;
    private static UserDeletionService service;

    @BeforeAll
    static void setUp() {
        String url = System.getenv("USER_TEST_DB_URL");
        String user = System.getenv().getOrDefault("USER_TEST_DB_USER", "postgres");
        String password = System.getenv().getOrDefault("USER_TEST_DB_PASSWORD", "");
        Flyway.configure().dataSource(url, user, password).schemas(SCHEMA).defaultSchema(SCHEMA).load().migrate();
        var dataSource = new DriverManagerDataSource(url, user, password);
        dataSource.setSchema(SCHEMA);
        jdbc = new JdbcTemplate(dataSource);
        transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        service = new UserDeletionService(jdbc);
    }

    @AfterAll
    static void tearDown() {
        if (jdbc != null) {
            jdbc.execute("DROP SCHEMA " + SCHEMA + " CASCADE");
        }
    }

    @Test
    void deletionRequiresBothMatchingAcknowledgementsAndIsIdempotent() {
        Long userId = jdbc.queryForObject("""
                INSERT INTO users(name, surname, password, email)
                VALUES ('Test', 'User', 'test-hash', 'deletion@example.com') RETURNING id
                """, Long.class);
        transactions.executeWithoutResult(status -> service.request(userId));
        transactions.executeWithoutResult(status -> service.request(userId));
        UUID requestId = jdbc.queryForObject("SELECT request_id FROM user_deletion_requests WHERE user_id = ?",
                UUID.class, userId);
        assertEquals(Boolean.TRUE, jdbc.queryForObject("SELECT deleting FROM users WHERE id = ?", Boolean.class, userId));

        transactions.executeWithoutResult(status -> service.acknowledge(new UserDeletionEvent(UUID.randomUUID(), userId, "finance")));
        assertEquals(Boolean.FALSE, jdbc.queryForObject("SELECT finance_done FROM user_deletion_requests WHERE user_id = ?", Boolean.class, userId));
        transactions.executeWithoutResult(status -> service.acknowledge(new UserDeletionEvent(requestId, userId, "finance")));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE id = ?", Integer.class, userId));
        transactions.executeWithoutResult(status -> service.acknowledge(new UserDeletionEvent(requestId, userId, "analytics")));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE id = ?", Integer.class, userId));
        assertNotNull(jdbc.queryForObject("SELECT completed_at FROM user_deletion_requests WHERE user_id = ?", java.time.OffsetDateTime.class, userId));
        transactions.executeWithoutResult(status -> service.acknowledge(new UserDeletionEvent(requestId, userId, "analytics")));
    }

    @Test
    void rolledBackDeletionDoesNotLeaveAnOutboxRequest() {
        Long userId = jdbc.queryForObject("""
                INSERT INTO users(name, surname, password, email)
                VALUES ('Test', 'User', 'test-hash', 'rollback@example.com') RETURNING id
                """, Long.class);
        transactions.executeWithoutResult(status -> {
            service.request(userId);
            status.setRollbackOnly();
        });
        assertEquals(Boolean.FALSE, jdbc.queryForObject("SELECT deleting FROM users WHERE id = ?", Boolean.class, userId));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM user_deletion_requests WHERE user_id = ?", Integer.class, userId));
    }
}
