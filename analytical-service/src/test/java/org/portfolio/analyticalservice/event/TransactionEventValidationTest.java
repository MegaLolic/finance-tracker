package org.portfolio.analyticalservice.event;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class TransactionEventValidationTest {
    @ParameterizedTest
    @MethodSource("invalidEvents")
    void invalidPayloadNeverReachesMongo(TransactionEvent event) {
        var mongo = mock(MongoTemplate.class);
        assertThrows(IllegalArgumentException.class, () -> new TransactionEventListener(mongo).onTransactionEvent(event));
        verifyNoInteractions(mongo);
    }

    static Stream<TransactionEvent> invalidEvents() {
        return Stream.of(
                event(null, 2L, 3L, 4L, "10", "EXPENSE", Instant.EPOCH, null),
                event(1L, null, 3L, 4L, "10", "EXPENSE", Instant.EPOCH, null),
                event(1L, 2L, 0L, 4L, "10", "EXPENSE", Instant.EPOCH, null),
                event(1L, 2L, 3L, -1L, "10", "EXPENSE", Instant.EPOCH, null),
                event(1L, 2L, 3L, 4L, null, "EXPENSE", Instant.EPOCH, null),
                event(1L, 2L, 3L, 4L, "0", "EXPENSE", Instant.EPOCH, null),
                event(1L, 2L, 3L, 4L, "-1", "EXPENSE", Instant.EPOCH, null),
                event(1L, 2L, 3L, 4L, "0.001", "EXPENSE", Instant.EPOCH, null),
                event(1L, 2L, 3L, 4L, "100000000000000000", "EXPENSE", Instant.EPOCH, null),
                event(1L, 2L, 3L, 4L, "10", "UNKNOWN", Instant.EPOCH, null),
                event(1L, 2L, 3L, 4L, "10", null, Instant.EPOCH, null),
                event(1L, 2L, 3L, 4L, "10", "EXPENSE", null, null),
                event(1L, 2L, 3L, 4L, "10", "EXPENSE", Instant.EPOCH, "x".repeat(501)));
    }

    @Test
    void nullEventIsRejectedWithoutWriting() {
        var mongo = mock(MongoTemplate.class);
        assertThrows(IllegalArgumentException.class, () -> new TransactionEventListener(mongo).onTransactionEvent(null));
        verifyNoInteractions(mongo);
    }

    private static TransactionEvent event(Long id, Long user, Long account, Long category, String amount,
            String categoryType, Instant createdAt, String description) {
        return new TransactionEvent(UUID.randomUUID(), "CREATED", id, 0L, user, account, category,
                amount == null ? null : new BigDecimal(amount), description, categoryType, createdAt);
    }
}
