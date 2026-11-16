package org.portfolio.analyticalservice.deletion;

import org.bson.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/** Repairs an upsert interrupted by a process crash between writing and checking the tombstone. */
@Configuration
@EnableScheduling
public class DeletedUserCleanup {
    private final MongoTemplate mongo;
    private final int batchSize;
    private long afterUserId;

    public DeletedUserCleanup(MongoTemplate mongo,
            @Value("${user-deletion.cleanup-batch-size:100}") int batchSize) {
        if (batchSize < 1) {
            throw new IllegalArgumentException("Deleted-user cleanup batch size must be positive");
        }
        this.mongo = mongo;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${user-deletion.cleanup-interval-ms:30000}")
    public void cleanup() {
        int processed = 0;
        try (var deleted = mongo.getCollection("deleted_users")
                .find(new Document("_id", new Document("$gt", afterUserId)))
                .sort(new Document("_id", 1)).limit(batchSize).iterator()) {
            while (deleted.hasNext()) {
                long userId = deleted.next().get("_id", Number.class).longValue();
                mongo.getCollection("raw_transactions").deleteMany(
                        new Document("userId", userId));
                // Move the cursor only after cleanup succeeds; failures are retried next time.
                afterUserId = userId;
                processed++;
            }
        }
        if (processed < batchSize) {
            afterUserId = 0;
        }
    }
}
