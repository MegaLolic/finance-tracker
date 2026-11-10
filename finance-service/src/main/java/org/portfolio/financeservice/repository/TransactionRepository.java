package org.portfolio.financeservice.repository;

import org.portfolio.financeservice.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface TransactionRepository extends JpaRepository<Transaction,Long>, JpaSpecificationExecutor<Transaction> {
    List<Transaction> findTransactionsByUserId(Long userId);

    Optional<Transaction> findTransactionByUserId(Long id, Long userId);
}
