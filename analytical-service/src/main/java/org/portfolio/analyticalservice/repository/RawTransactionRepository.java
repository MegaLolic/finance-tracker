package org.portfolio.analyticalservice.repository;

import org.portfolio.analyticalservice.document.RawTransaction;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface RawTransactionRepository extends MongoRepository<RawTransaction, Long> {
}
