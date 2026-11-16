package org.portfolio.analyticalservice.event;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.FindIterable;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.junit.jupiter.api.Test;
import org.portfolio.analyticalservice.deletion.UserDeletionEvent;
import org.portfolio.analyticalservice.deletion.UserDeletionListener;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserDeletionTest {
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"null", "{\"requestId\":\"b935c238-0762-4cb0-a35a-9b037d586e05\",\"userId\":2,\"service\":\"analytics\"}"})
    @SuppressWarnings("unchecked")
    void invalidRequestIsRejectedBeforeCleanup(String json) {
        var mongo = mock(MongoTemplate.class);
        KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
        var listener = new UserDeletionListener(mongo, new JsonMapper(), kafka);

        assertThrows(IllegalArgumentException.class, () -> listener.delete(json));
        verifyNoInteractions(mongo, kafka);
    }

    @Test
    @SuppressWarnings("unchecked")
    void lateTransactionIsIgnoredAfterUserTombstone() {
        var mongo = mock(MongoTemplate.class);
        MongoCollection<Document> tombstones = mock(MongoCollection.class);
        FindIterable<Document> found = mock(FindIterable.class);
        when(mongo.getCollection("deleted_users")).thenReturn(tombstones);
        when(tombstones.find(any(Bson.class))).thenReturn(found);
        when(found.first()).thenReturn(new Document("_id", 2L));

        new TransactionEventListener(mongo).onTransactionEvent(new TransactionEvent(
                UUID.randomUUID(), "CREATED", 1L, 0L, 2L, 3L, 4L,
                BigDecimal.TEN, "late", "EXPENSE", Instant.EPOCH));

        verify(mongo, never()).getCollection("raw_transactions");
    }

    @Test
    @SuppressWarnings("unchecked")
    void acknowledgementIsSentOnlyAfterTombstoneAndCleanup() throws Exception {
        var mongo = mock(MongoTemplate.class);
        MongoCollection<Document> tombstones = mock(MongoCollection.class);
        MongoCollection<Document> data = mock(MongoCollection.class);
        KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
        when(mongo.getCollection("deleted_users")).thenReturn(tombstones);
        when(mongo.getCollection("raw_transactions")).thenReturn(data);
        when(kafka.send(anyString(), anyString(), anyString())).thenReturn(CompletableFuture.completedFuture(null));
        var mapper = new JsonMapper();
        var request = new UserDeletionEvent(UUID.randomUUID(), 2L, null);
        var listener = new UserDeletionListener(mongo, mapper, kafka);

        listener.delete(mapper.writeValueAsString(request));
        listener.delete(mapper.writeValueAsString(request));

        var order = inOrder(tombstones, data, kafka);
        order.verify(tombstones).replaceOne(any(Bson.class), any(Document.class), any(com.mongodb.client.model.ReplaceOptions.class));
        order.verify(data).deleteMany(new Document("userId", 2L));
        order.verify(kafka).send(eq("user-deletion-completed"), eq("2"), anyString());
        verify(kafka, times(2)).send(eq("user-deletion-completed"), eq("2"), anyString());
    }

    @Test
    @SuppressWarnings("unchecked")
    void mongoFailurePreventsAcknowledgement() {
        var mongo = mock(MongoTemplate.class);
        MongoCollection<Document> tombstones = mock(MongoCollection.class);
        MongoCollection<Document> data = mock(MongoCollection.class);
        KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
        when(mongo.getCollection("deleted_users")).thenReturn(tombstones);
        when(mongo.getCollection("raw_transactions")).thenReturn(data);
        when(data.deleteMany(any(Bson.class))).thenThrow(new IllegalStateException("Mongo unavailable"));
        var mapper = new JsonMapper();
        var listener = new UserDeletionListener(mongo, mapper, kafka);
        var json = mapper.writeValueAsString(new UserDeletionEvent(UUID.randomUUID(), 2L, null));

        assertThrows(IllegalStateException.class, () -> listener.delete(json));
        verifyNoInteractions(kafka);
    }
}
