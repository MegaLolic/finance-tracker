package org.portfolio.analyticalservice.config;

import com.mongodb.client.model.Indexes;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;

@Configuration
@RequiredArgsConstructor
public class MongoIndexConfig {
    private final MongoTemplate mongo;

    @PostConstruct
    public void createIndexes() {
        // createIndex is idempotent and also covers cleanup's userId filter.
        mongo.getCollection("raw_transactions").createIndex(Indexes.ascending("userId"));
        mongo.getCollection("raw_transactions").createIndex(
                Indexes.ascending("userId", "accountId", "deleted", "createdAt"));
    }
}
