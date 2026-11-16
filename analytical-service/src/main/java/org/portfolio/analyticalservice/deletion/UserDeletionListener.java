package org.portfolio.analyticalservice.deletion;

import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class UserDeletionListener {
    private final MongoTemplate mongo;
    private final ObjectMapper mapper;
    private final KafkaTemplate<String, String> kafka;

    @KafkaListener(topics = "user-deletion-requests", groupId = "analytics-user-deletion",
            properties = {"value.deserializer=org.apache.kafka.common.serialization.StringDeserializer"})
    public void delete(String json) throws Exception {
        var request = mapper.readValue(json, UserDeletionEvent.class);
        // Persist the tombstone BEFORE removing documents. Retried and late events cannot restore them.
        mongo.getCollection("deleted_users").replaceOne(new Document("_id", request.userId()),
                new Document("_id", request.userId()), new com.mongodb.client.model.ReplaceOptions().upsert(true));
        mongo.getCollection("raw_transactions").deleteMany(new Document("userId", request.userId()));
        var completed = new UserDeletionEvent(request.requestId(), request.userId(), "analytics");
        kafka.send("user-deletion-completed", request.userId().toString(), mapper.writeValueAsString(completed))
                .get(10, TimeUnit.SECONDS);
    }
}
