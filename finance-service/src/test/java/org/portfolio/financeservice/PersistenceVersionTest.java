package org.portfolio.financeservice;

import jakarta.persistence.OptimisticLockException;
import org.flywaydb.core.Flyway;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.portfolio.financeservice.entity.*;

import java.math.BigDecimal;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Runs the real Flyway migrations and Hibernate against an explicitly supplied test database. */
@EnabledIfEnvironmentVariable(named = "FINANCE_TEST_DB_URL", matches = ".+")
class PersistenceVersionTest {
    private static final String SCHEMA = "version_test_" + UUID.randomUUID().toString().replace("-", "");
    private static SessionFactory factory;
    private static String url;
    private static String username;
    private static String password;
    private Long accountId;
    private Long categoryId;
    private Long transactionId;

    @BeforeAll
    static void setupDatabase() {
        url = System.getenv("FINANCE_TEST_DB_URL");
        username = System.getenv().getOrDefault("FINANCE_TEST_DB_USER", "postgres");
        password = System.getenv().getOrDefault("FINANCE_TEST_DB_PASSWORD", "");
        Flyway.configure().dataSource(url, username, password)
                .schemas(SCHEMA).defaultSchema(SCHEMA).load().migrate();
        factory = new Configuration()
                .addAnnotatedClass(Account.class)
                .addAnnotatedClass(Category.class)
                .addAnnotatedClass(Transaction.class)
                .setProperty("hibernate.connection.url", url)
                .setProperty("hibernate.connection.username", username)
                .setProperty("hibernate.connection.password", password)
                .setProperty("hibernate.default_schema", SCHEMA)
                .setProperty("hibernate.physical_naming_strategy",
                        "org.hibernate.boot.model.naming.PhysicalNamingStrategySnakeCaseImpl")
                .setProperty("hibernate.hbm2ddl.auto", "validate")
                .buildSessionFactory();
    }

    @AfterAll
    static void cleanupDatabase() throws Exception {
        if (factory != null) {
            factory.close();
        }
        if (url != null) {
            try (var connection = DriverManager.getConnection(url, username, password);
                 var statement = connection.createStatement()) {
                statement.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
            }
        }
    }

    @BeforeEach
    void createFixtures() {
        factory.inTransaction(session -> {
            Account account = Account.builder().userId(1L).name("Test account")
                    .balance(new BigDecimal("100.25")).currency(CurrencyEnum.BYN).build();
            Category category = Category.builder().userId(1L).name(UUID.randomUUID().toString())
                    .type(CategoryEnum.EXPENSE).build();
            session.persist(account);
            session.persist(category);
            Transaction transaction = Transaction.builder().userId(1L).accountId(account.getId())
                    .categoryId(category.getId()).amount(new BigDecimal("5.00"))
                    .description("Original").createdAt(Instant.now()).build();
            session.persist(transaction);
            accountId = account.getId();
            categoryId = category.getId();
            transactionId = transaction.getId();
        });
    }

    @Test
    void conflictingBalanceUpdateRollsBackTheEntireFinancialOperation() {
        Long losingTransactionId;
        try (var first = factory.openSession(); var second = factory.openSession()) {
            var firstTx = first.beginTransaction();
            var secondTx = second.beginTransaction();
            Account a = first.find(Account.class, accountId);
            Account b = second.find(Account.class, accountId);
            assertEquals(0L, a.getVersion());
            a.setBalance(a.getBalance().subtract(new BigDecimal("10.00")));
            firstTx.commit();

            Transaction losingOperation = Transaction.builder().userId(1L).accountId(accountId)
                    .categoryId(categoryId).amount(new BigDecimal("20.00"))
                    .createdAt(Instant.now()).build();
            second.persist(losingOperation);
            losingTransactionId = losingOperation.getId();
            b.setBalance(b.getBalance().subtract(new BigDecimal("20.00")));
            assertThrows(OptimisticLockException.class, second::flush);
            secondTx.rollback();
        }
        try (var session = factory.openSession()) {
            Account account = session.find(Account.class, accountId);
            assertEquals(new BigDecimal("90.25"), account.getBalance());
            assertEquals(1L, account.getVersion());
            assertNull(session.find(Transaction.class, losingTransactionId));
        }
    }

    @Test
    void outdatedTransactionCannotBeEdited() {
        try (var first = factory.openSession(); var second = factory.openSession()) {
            var firstTx = first.beginTransaction();
            var secondTx = second.beginTransaction();
            Transaction a = first.find(Transaction.class, transactionId);
            Transaction b = second.find(Transaction.class, transactionId);
            a.setDescription("First edit");
            firstTx.commit();
            b.setDescription("Stale edit");
            assertThrows(OptimisticLockException.class, second::flush);
            secondTx.rollback();
        }
        try (var session = factory.openSession()) {
            Transaction saved = session.find(Transaction.class, transactionId);
            assertEquals("First edit", saved.getDescription());
            assertEquals(1L, saved.getVersion());
        }
    }

    @Test
    void outdatedTransactionCannotBeDeleted() {
        try (var first = factory.openSession(); var second = factory.openSession()) {
            var firstTx = first.beginTransaction();
            var secondTx = second.beginTransaction();
            Transaction a = first.find(Transaction.class, transactionId);
            Transaction b = second.find(Transaction.class, transactionId);
            a.setDescription("Updated before deletion");
            firstTx.commit();
            second.remove(b);
            assertThrows(OptimisticLockException.class, second::flush);
            secondTx.rollback();
        }
        try (var session = factory.openSession()) {
            assertNotNull(session.find(Transaction.class, transactionId));
        }
    }
}
