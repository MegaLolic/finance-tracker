package org.portfolio.financeservice.repository;

import org.hibernate.sql.results.graph.FetchList;
import org.portfolio.financeservice.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction,Long>, JpaSpecificationExecutor<Transaction> {
    List<Transaction> findTransactionsByUserId(Long userId);
}
