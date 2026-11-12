package org.portfolio.financeservice.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransactionEvent(
        UUID eventId, String eventType, Long transactionId, Long version,
        Long userId, Long accountId, Long categoryId,
        BigDecimal amount, String description, String categoryType, Instant createdAt
) {
}
