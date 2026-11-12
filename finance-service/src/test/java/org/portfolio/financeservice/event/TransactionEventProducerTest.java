package org.portfolio.financeservice.event;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.event.TransactionalEventListenerFactory;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.TransactionDefinition;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class TransactionEventProducerTest {
    private AnnotationConfigApplicationContext context;
    private KafkaTemplate<String, TransactionCreatedEvent> kafka;
    private TransactionTemplate transactions;
    private final TransactionCreatedEvent event = new TransactionCreatedEvent(
            1L, 2L, 3L, 4L, BigDecimal.TEN, "Test", "EXPENSE", Instant.EPOCH);

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setup() {
        kafka = mock(KafkaTemplate.class);
        when(kafka.send(anyString(), anyString(), any(TransactionCreatedEvent.class)))
                .thenReturn(CompletableFuture.completedFuture(null));
        context = new AnnotationConfigApplicationContext();
        context.registerBean(TransactionalEventListenerFactory.class);
        context.registerBean(TransactionEventProducer.class,
                () -> new TransactionEventProducer(kafka, "transaction-events"));
        context.refresh();
        transactions = new TransactionTemplate(new AbstractPlatformTransactionManager() {
            @Override
            protected Object doGetTransaction() { return new Object(); }
            @Override
            protected void doBegin(Object tx, TransactionDefinition definition) { }
            @Override
            protected void doCommit(DefaultTransactionStatus status) { }
            @Override
            protected void doRollback(DefaultTransactionStatus status) { }
        });
    }

    @AfterEach
    void cleanup() { context.close(); }

    @Test
    void sendsOnlyAfterCommit() {
        transactions.executeWithoutResult(status -> {
            context.publishEvent(event);
            verifyNoInteractions(kafka);
        });
        verify(kafka).send("transaction-events", "1", event);
    }

    @Test
    void rollbackDoesNotSendAnEvent() {
        assertThrows(IllegalStateException.class, () -> transactions.executeWithoutResult(status -> {
            context.publishEvent(event);
            throw new IllegalStateException("Simulated conflict");
        }));
        verifyNoInteractions(kafka);
    }

    @Test
    void kafkaFailureDoesNotReportTheCommittedOperationAsFailed() {
        when(kafka.send("transaction-events", "1", event)).thenThrow(new IllegalStateException("Kafka down"));
        assertDoesNotThrow(() -> transactions.executeWithoutResult(status -> context.publishEvent(event)));
        verify(kafka).send("transaction-events", "1", event);
    }

    @Test
    void noTransactionDoesNotSendAnEvent() {
        context.publishEvent(event);
        verifyNoInteractions(kafka);
    }
}
