package org.portfolio.analyticalservice.event;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.Instant;

public record TransactionCreatedEvent (
        Long transactionId,
        Long accountId,
        Long userId,
        Long categoryId,
        BigDecimal amount,
        String description,
        String categoryType,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Instant createdAt
) {
}