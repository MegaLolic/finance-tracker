package org.portfolio.financeservice.repository;

import org.portfolio.financeservice.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AccountRepository extends JpaRepository<Account,Long> {
    List<Account> findAccountsByUserId(Long userId);
    Optional<Account> findByIdAndUserId(Long id, Long userId);
}
