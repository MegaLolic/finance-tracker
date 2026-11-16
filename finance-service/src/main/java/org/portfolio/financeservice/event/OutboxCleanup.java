package org.portfolio.financeservice.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class OutboxCleanup {
    private final JdbcTemplate jdbc;
    private final int retentionDays;
    private final int batchSize;

    public OutboxCleanup(JdbcTemplate jdbc,
                         @Value("${outbox.retention-days:7}") int retentionDays,
                         @Value("${outbox.cleanup-batch-size:1000}") int batchSize) {
        if (retentionDays < 1 || batchSize < 1) {
            throw new IllegalArgumentException("Outbox retention and cleanup batch size must be positive");
        }
        this.jdbc = jdbc;
        this.retentionDays = retentionDays;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${outbox.cleanup-interval-ms:3600000}",
            initialDelayString = "${outbox.cleanup-interval-ms:3600000}")
    public void cleanPublished() {
        int deleted = jdbc.update("""
                WITH expired AS (
                    SELECT sequence FROM outbox_events
                    WHERE published_at < CURRENT_TIMESTAMP - (? * INTERVAL '1 day')
                    ORDER BY published_at, sequence
                    LIMIT ?
                    FOR UPDATE SKIP LOCKED
                )
                DELETE FROM outbox_events e USING expired
                WHERE e.sequence = expired.sequence
                """, retentionDays, batchSize);
        if (deleted > 0) {
            log.info("Removed {} published outbox events older than {} days", deleted, retentionDays);
        }
    }
}
