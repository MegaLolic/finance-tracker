package org.portfolio.analyticalservice.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.portfolio.analyticalservice.document.RawTransaction;
import org.springframework.data.mongodb.core.MongoTemplate;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.result.UpdateResult;
import org.bson.Document;
import java.util.List;
import java.util.Set;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionEventListener {
    private static final long LEGACY_EVENT_VERSION = -1L;
    private static final long UNVERSIONED_DOCUMENT = -2L;
    private static final Set<String> EVENT_TYPES = Set.of("CREATED", "UPDATED", "DELETED");

    private final MongoTemplate mongo;

    @KafkaListener(topics = "${topic.name}", groupId = "analytical-service")
    public void onTransactionEvent(TransactionEvent event) {
        log.info("Received event: {}", event);

        RawTransaction state = toState(event);
        UpdateResult result = saveIfNewer(state);

        log.info("Applied event {}: transaction={}, version={}, modified={}, inserted={}",
                event.eventId(), state.getId(), state.getVersion(), result.getModifiedCount(), result.getUpsertedId());
    }

    private RawTransaction toState(TransactionEvent event) {
        String type = event.eventType();
        long version;
        if (isLegacyEvent(event)) {
            type = "CREATED";
            version = LEGACY_EVENT_VERSION;
        } else {
            version = requireVersion(event);
        }

        if (event.transactionId() == null || type == null || !EVENT_TYPES.contains(type)) {
            throw new IllegalArgumentException("Invalid transaction event: " + event);
        }

        String eventId = null;
        if (event.eventId() != null) {
            eventId = event.eventId().toString();
        }

        return RawTransaction.builder()
                .id(event.transactionId())
                .version(version)
                .eventId(eventId)
                .deleted("DELETED".equals(type))
                .userId(event.userId())
                .accountId(event.accountId())
                .categoryId(event.categoryId())
                .description(event.description())
                .categoryType(event.categoryType())
                .amount(event.amount())
                .createdAt(event.createdAt())
                .build();
    }

    private UpdateResult saveIfNewer(RawTransaction state) {
        Document replacement = new Document();
        mongo.getConverter().write(state, replacement);

        List<Document> update = List.of(replaceIfNewer(replacement, state.getVersion()));
        UpdateOptions options = new UpdateOptions().upsert(true);
        return mongo.getCollection("raw_transactions")
                .updateOne(Filters.eq("_id", state.getId()), update, options);
    }

    private Document replaceIfNewer(Document replacement, long incomingVersion) {
        Document storedVersion = new Document("$ifNull", List.of("$version", UNVERSIONED_DOCUMENT));
        Document incomingIsNewer = new Document("$lt", List.of(storedVersion, incomingVersion));
        Document newState = new Document("$literal", replacement);

        // If the incoming version is newer, use its state; otherwise keep the existing document.
        Document selectedState = new Document("$cond", List.of(incomingIsNewer, newState, "$$ROOT"));
        return new Document("$replaceWith", selectedState);
    }

    private boolean isLegacyEvent(TransactionEvent event) {
        return event.eventType() == null && event.version() == null && event.eventId() == null;
    }

    private long requireVersion(TransactionEvent event) {
        if (event.version() == null || event.version() < 0 || event.eventId() == null) {
            throw new IllegalArgumentException("Event ID and a non-negative version are required");
        }
        return event.version();
    }
}
