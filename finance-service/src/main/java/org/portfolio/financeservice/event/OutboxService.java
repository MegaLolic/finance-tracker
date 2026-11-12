package org.portfolio.financeservice.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.portfolio.financeservice.entity.Transaction;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    @Transactional(propagation = Propagation.MANDATORY)
    public void append(String eventType, Transaction transaction, String categoryType) {
        TransactionEvent event = createEvent(eventType, transaction, categoryType);
        saveEvent(event);
        log.info("Stored outbox event: {}", event);
    }

    private TransactionEvent createEvent(String eventType, Transaction transaction, String categoryType) {
        long version = transaction.getVersion();
        if ("DELETED".equals(eventType)) {
            version++;
        }

        return new TransactionEvent(
                UUID.randomUUID(),
                eventType,
                transaction.getId(),
                version,
                transaction.getUserId(),
                transaction.getAccountId(),
                transaction.getCategoryId(),
                transaction.getAmount(),
                transaction.getDescription(),
                categoryType,
                transaction.getCreatedAt()
        );
    }

    private void saveEvent(TransactionEvent event) {
        String json = mapper.writeValueAsString(event);
        jdbc.update("""
                INSERT INTO outbox_events(id, transaction_id, aggregate_version, event_type, payload)
                VALUES (?, ?, ?, ?, ?::jsonb)
                """, event.eventId(), event.transactionId(), event.version(), event.eventType(), json);
    }
}
