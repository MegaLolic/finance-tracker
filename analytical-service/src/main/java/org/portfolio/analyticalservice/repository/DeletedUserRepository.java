package org.portfolio.analyticalservice.repository;

import org.portfolio.analyticalservice.document.DeletedUser;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface DeletedUserRepository extends MongoRepository<DeletedUser, Long> {
}
