package org.portfolio.analyticalservice.repository;

import org.portfolio.analyticalservice.document.RawTransaction;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import java.util.List;
import java.time.Instant;

public interface RawTransactionRepository extends MongoRepository<RawTransaction, Long> {
    List<RawTransaction> findAllByUserIdAndDeletedFalse(Long userId);

    @Query("{'userId': ?0, 'accountId': ?1, 'deleted': false, 'createdAt': {'$gte': ?2, '$lt': ?3}}")
    List<RawTransaction> findAllByUserIdAndAccountIdAndDeletedFalseAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            Long userId, Long accountId, Instant from, Instant to);
}
