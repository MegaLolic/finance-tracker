package org.portfolio.analyticalservice.deletion;

import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/** Repairs an upsert interrupted by a process crash between writing and checking the tombstone. */
@Configuration
@EnableScheduling
@RequiredArgsConstructor
public class DeletedUserCleanup {
    private final MongoTemplate mongo;

    @Scheduled(fixedDelayString = "${user-deletion.cleanup-interval-ms:30000}")
    public void cleanup() {
        try (var deleted = mongo.getCollection("deleted_users").find().iterator()) {
            while (deleted.hasNext()) {
                mongo.getCollection("raw_transactions").deleteMany(
                        new Document("userId", deleted.next().get("_id")));
            }
        }
    }
}
