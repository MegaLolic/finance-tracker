package org.portfolio.financeservice.service;

import org.portfolio.financeservice.client.UserClient;
import org.portfolio.financeservice.dto.TransactionDto;
import org.portfolio.financeservice.dto.TransactionFilterDto;
import org.portfolio.financeservice.entity.Account;
import org.portfolio.financeservice.entity.Category;
import org.portfolio.financeservice.entity.Transaction;
import org.portfolio.financeservice.event.OutboxService;
import org.portfolio.financeservice.exception.ResourceNotFoundException;
import org.portfolio.financeservice.repository.AccountRepository;
import org.portfolio.financeservice.repository.CategoryRepository;
import org.portfolio.financeservice.repository.TransactionRepository;
import org.portfolio.financeservice.repository.specification.TransactionSpecification;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

@Service
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final CategoryRepository categoryRepository;
    private final UserClient userClient;
    private final AccountService accountService;
    private final OutboxService outbox;

    public TransactionService(
            TransactionRepository transactionRepository,
            AccountRepository accountRepository,
            CategoryRepository categoryRepository,
            UserClient userClient,
            AccountService accountService,
            OutboxService outbox) {

        this.transactionRepository = transactionRepository;
        this.accountRepository = accountRepository;
        this.categoryRepository = categoryRepository;
        this.userClient = userClient;
        this.accountService = accountService;
        this.outbox = outbox;
    }

    @Transactional
    public TransactionDto createTransaction(Long userId, TransactionDto input) {
        userClient.getUserById(userId);

        Account account = accountRepository
                .findByIdAndUserId(input.getAccountId(), userId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Account", input.getAccountId()));

        Category category = categoryRepository
                .findByIdAndUserId(input.getCategoryId(), userId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Category", input.getCategoryId()));

        Transaction createdTransaction = Transaction.builder()
                .userId(userId)
                .accountId(account.getId())
                .categoryId(category.getId())
                .amount(input.getAmount())
                .description(input.getDescription())
                .createdAt(Instant.now())
                .build();

        Transaction saved = transactionRepository.save(createdTransaction);

        BigDecimal balanceChanged = getBalanceChange(saved.getAmount(), category);

        accountService.changeBalance(
                saved.getAccountId(),
                userId,
                balanceChanged
        );

        transactionRepository.flush();

        outbox.append("CREATED", saved, category.getType().name());

        return toDto(saved);
    }

    private TransactionDto toDto(Transaction transaction) {
        return TransactionDto.builder()
                .id(transaction.getId())
                .version(transaction.getVersion())
                .accountId(transaction.getAccountId())
                .categoryId(transaction.getCategoryId())
                .amount(transaction.getAmount())
                .description(transaction.getDescription())
                .createdAt(transaction.getCreatedAt())
                .build();
    }

    public TransactionDto getTransactionById(Long id, Long userId) {
        return transactionRepository
                .findByIdAndUserId(id, userId)
                .map(this::toDto)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction", id));
    }

    @Transactional
    public TransactionDto updateTransaction(Long id, Long userId, TransactionDto input) {

        Transaction updatedTransaction = transactionRepository
                .findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction", id));

        if (!Objects.equals(input.getVersion(), updatedTransaction.getVersion())) {
            throw new ObjectOptimisticLockingFailureException(Transaction.class, id);
        }

        Account oldAccount = accountRepository
                .findByIdAndUserId(updatedTransaction.getAccountId(), userId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Account", updatedTransaction.getAccountId()));

        Category oldCategory = categoryRepository
                .findByIdAndUserId(updatedTransaction.getCategoryId(), userId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Category", updatedTransaction.getCategoryId()));

        Account newAccount = accountRepository
                .findByIdAndUserId(input.getAccountId(), userId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Account", input.getAccountId()));

        Category newCategory = categoryRepository
                .findByIdAndUserId(input.getCategoryId(), userId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Category", input.getCategoryId()));

        BigDecimal oldBalanceChange = getBalanceChange(
                updatedTransaction.getAmount(),
                oldCategory
        ).negate();

        accountService.changeBalance(
                oldAccount.getId(),
                userId,
                oldBalanceChange
        );

        updatedTransaction.setAccountId(newAccount.getId());
        updatedTransaction.setCategoryId(newCategory.getId());
        updatedTransaction.setAmount(input.getAmount());
        updatedTransaction.setDescription(input.getDescription());

        BigDecimal newBalanceChange = getBalanceChange(
                updatedTransaction.getAmount(),
                newCategory
        );

        accountService.changeBalance(
                newAccount.getId(),
                userId,
                newBalanceChange
        );

        Transaction saved = transactionRepository.saveAndFlush(updatedTransaction);
        outbox.append("UPDATED", saved, newCategory.getType().name());
        return toDto(saved);
    }

    @Transactional
    public void deleteTransaction(Long id, Long userId, Long version) {

        Transaction transaction = transactionRepository
                .findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction", id));

        if (!Objects.equals(version, transaction.getVersion())) {
            throw new ObjectOptimisticLockingFailureException(Transaction.class, id);
        }

        Category category = categoryRepository
                .findByIdAndUserId(transaction.getCategoryId(), userId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Category", transaction.getCategoryId()));

        accountService.changeBalance(
                transaction.getAccountId(),
                userId,
                getBalanceChange(transaction.getAmount(), category).negate()
        );

        outbox.append("DELETED", transaction, category.getType().name());
        transactionRepository.delete(transaction);
    }

    public List<TransactionDto> getTransactions(TransactionFilterDto filter) {
        if (filter.getUserId() == null) {
            return List.of();
        }

        Specification<Transaction> specification =
                TransactionSpecification.filter(filter);

        return transactionRepository.findAll(specification)
                .stream()
                .map(this::toDto)
                .toList();
    }

    private BigDecimal getBalanceChange(BigDecimal amount, Category category) {
        return switch (category.getType()) {
            case INCOME -> amount;
            case EXPENSE -> amount.negate();
        };
    }

    public List<TransactionDto> getTransactionsByUserId(Long userId) {
        return transactionRepository.findTransactionsByUserId(userId)
                .stream()
                .map(this::toDto)
                .toList();
    }
}
