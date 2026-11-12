package org.portfolio.financeservice.repository;

import org.portfolio.financeservice.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface TransactionRepository extends JpaRepository<Transaction,Long>, JpaSpecificationExecutor<Transaction> {
    List<Transaction> findTransactionsByUserId(Long userId);

    Optional<Transaction> findByIdAndUserId(Long id, Long userId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<Transaction> findAllByAccountIdAndUserIdOrderById(Long accountId, Long userId);
}
