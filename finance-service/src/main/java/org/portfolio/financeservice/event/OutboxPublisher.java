package org.portfolio.financeservice.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class OutboxPublisher {
    private static final long PUBLISHER_LOCK_ID = 74412001L;

    private final JdbcTemplate jdbc;
    private final KafkaTemplate<String, String> kafka;
    private final TransactionTemplate transactions;
    private final String topic;
    private final int batchSize;
    private final long timeoutMs;
    private final long retryDelayMs;

    public OutboxPublisher(JdbcTemplate jdbc, KafkaTemplate<String, String> kafka, TransactionTemplate transactions,
                           @Value("${topic.name}") String topic,
                           @Value("${outbox.batch-size:20}") int batchSize,
                           @Value("${outbox.send-timeout-ms:10000}") long timeoutMs,
                           @Value("${outbox.retry-delay-ms:5000}") long retryDelayMs) {
        if (batchSize < 1 || timeoutMs < 1 || retryDelayMs < 1) {
            throw new IllegalArgumentException("Outbox batch size, timeout and retry delay must be positive");
        }
        this.jdbc = jdbc;
        this.kafka = kafka;
        this.transactions = transactions;
        this.topic = topic;
        this.batchSize = batchSize;
        this.timeoutMs = timeoutMs;
        this.retryDelayMs = retryDelayMs;
    }

    @Scheduled(fixedDelayString = "${outbox.poll-interval-ms:1000}")
    public void publishPending() {
        try {
            transactions.executeWithoutResult(status -> publishBatch());
        } catch (RuntimeException failure) {
            log.error("Outbox polling failed; pending events remain in PostgreSQL", failure);
        }
    }

    private void publishBatch() {
        if (!acquirePublisherLock()) {
            return;
        }

        for (PendingEvent event : findPendingEvents()) {
            if (!sendToKafka(event)) {
                if (Thread.currentThread().isInterrupted()) {
                    return;
                }
                continue;
            }
            markPublished(event);
        }
    }

    private boolean acquirePublisherLock() {
        // Only one relay may publish at a time. PostgreSQL releases the lock at transaction end.
        Boolean acquired = jdbc.queryForObject(
                "SELECT pg_try_advisory_xact_lock(?)", Boolean.class, PUBLISHER_LOCK_ID);
        return Boolean.TRUE.equals(acquired);
    }

    private List<PendingEvent> findPendingEvents() {
        // A failed event must be sent before later events for the same transaction.
        return jdbc.query("""
                SELECT e.sequence, e.id, e.transaction_id, e.payload::text AS payload
                FROM outbox_events e
                WHERE e.published_at IS NULL
                  AND e.next_attempt_at <= CURRENT_TIMESTAMP
                  AND NOT EXISTS (
                      SELECT 1 FROM outbox_events older
                      WHERE older.transaction_id = e.transaction_id
                        AND older.published_at IS NULL
                        AND older.sequence < e.sequence
                  )
                ORDER BY e.sequence
                LIMIT ?
                FOR UPDATE OF e SKIP LOCKED
                """, (row, rowNumber) -> new PendingEvent(
                        row.getLong("sequence"),
                        row.getString("id"),
                        row.getLong("transaction_id"),
                        row.getString("payload")
                ), batchSize);
    }

    private boolean sendToKafka(PendingEvent event) {
        try {
            String key = event.transactionId().toString();
            var delivery = kafka.send(topic, key, event.payload());
            delivery.get(timeoutMs, TimeUnit.MILLISECONDS);
            return true;
        } catch (Exception failure) {
            if (failure instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            scheduleRetry(event, failure);
            return false;
        }
    }

    private void scheduleRetry(PendingEvent event, Exception failure) {
        jdbc.update("""
                UPDATE outbox_events
                SET attempts = attempts + 1,
                    last_error = ?,
                    next_attempt_at = CURRENT_TIMESTAMP + (? * INTERVAL '1 millisecond')
                WHERE sequence = ?
                """, failure.toString(), retryDelayMs, event.sequence());
        log.error("Outbox event {} will be retried", event.id(), failure);
    }

    private void markPublished(PendingEvent event) {
        // DB errors must roll back the batch, even when Kafka has already accepted a message.
        jdbc.update("""
                UPDATE outbox_events
                SET published_at = CURRENT_TIMESTAMP,
                    attempts = attempts + 1,
                    last_error = NULL
                WHERE sequence = ?
                """, event.sequence());
        log.info("Published outbox event {} for transaction {}", event.id(), event.transactionId());
    }

    private record PendingEvent(Long sequence, String id, Long transactionId, String payload) { }
}
