package org.portfolio.financeservice.service;

import org.portfolio.financeservice.client.UserClient;
import org.portfolio.financeservice.dto.TransactionDto;
import org.portfolio.financeservice.dto.TransactionFilterDto;
import org.portfolio.financeservice.entity.Account;
import org.portfolio.financeservice.entity.Category;
import org.portfolio.financeservice.entity.Transaction;
import org.portfolio.financeservice.event.TransactionCreatedEvent;
import org.portfolio.financeservice.event.TransactionEventProducer;
import org.portfolio.financeservice.exception.ResourceNotFoundException;
import org.portfolio.financeservice.repository.AccountRepository;
import org.portfolio.financeservice.repository.CategoryRepository;
import org.portfolio.financeservice.repository.TransactionRepository;
import org.portfolio.financeservice.repository.specification.TransactionSpecification;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Service
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final CategoryRepository categoryRepository;
    private final UserClient userClient;
    private final AccountService accountService;
    private final TransactionEventProducer transactionEventProducer;

    public TransactionService(
            TransactionRepository transactionRepository,
            AccountRepository accountRepository,
            CategoryRepository categoryRepository,
            UserClient userClient,
            AccountService accountService,
            TransactionEventProducer transactionEventProducer) {

        this.transactionRepository = transactionRepository;
        this.accountRepository = accountRepository;
        this.categoryRepository = categoryRepository;
        this.userClient = userClient;
        this.accountService = accountService;
        this.transactionEventProducer = transactionEventProducer;
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

        transactionEventProducer.sendTransactionCreated(
                new TransactionCreatedEvent(
                        saved.getId(),
                        saved.getUserId(),
                        saved.getAccountId(),
                        saved.getCategoryId(),
                        saved.getAmount(),
                        saved.getDescription(),
                        category.getType().name(),
                        saved.getCreatedAt()
                )
        );

        return toDto(saved);
    }

    private TransactionDto toDto(Transaction transaction) {
        return TransactionDto.builder()
                .id(transaction.getId())
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
    public void updateTransaction(Long id, Long userId, TransactionDto input) {

        Transaction updatedTransaction = transactionRepository
                .findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction", id));

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

        transactionRepository.save(updatedTransaction);
    }

    @Transactional
    public void deleteTransaction(Long id, Long userId) {

        Transaction transaction = transactionRepository
                .findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction", id));

        Category category = categoryRepository
                .findByIdAndUserId(transaction.getCategoryId(), userId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Category", transaction.getCategoryId()));

        accountService.changeBalance(
                transaction.getAccountId(),
                userId,
                getBalanceChange(transaction.getAmount(), category).negate()
        );

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