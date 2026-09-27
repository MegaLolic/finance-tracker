package org.portfolio.financeservice.service;

import org.portfolio.financeservice.client.UserClient;
import org.portfolio.financeservice.dto.CategoryDto;
import org.portfolio.financeservice.dto.TransactionDto;
import org.portfolio.financeservice.dto.TransactionFilterDto;
import org.portfolio.financeservice.entity.Account;
import org.portfolio.financeservice.entity.Category;
import org.portfolio.financeservice.entity.Transaction;
import org.portfolio.financeservice.event.TransactionCreatedEvent;
import org.portfolio.financeservice.event.TransactionEventProducer;
import org.portfolio.financeservice.exception.BusinessException;
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

    public TransactionService(TransactionRepository transactionRepository, AccountRepository accountRepository, CategoryRepository categoryRepository, UserClient userClient, AccountService accountService, TransactionEventProducer transactionEventProducer) {
        this.transactionRepository = transactionRepository;
        this.accountRepository = accountRepository;
        this.categoryRepository = categoryRepository;
        this.userClient = userClient;
        this.accountService = accountService;
        this.transactionEventProducer = transactionEventProducer;
    }

    public TransactionDto createTransaction(Long userId, TransactionDto input) {
        userClient.getUserById(userId);

        Account account= accountRepository
                .findById(input.getAccountId())
                .orElseThrow(()->new ResourceNotFoundException("Account", input.getAccountId()));
        Category category = categoryRepository
                .findById(input.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category",input.getCategoryId()));
        if (!account.getUserId().equals(userId)) {
            throw new BusinessException("Account does not belong to this user");
        }
        if (!category.getUserId().equals(userId)) {
            throw new BusinessException("Category does not belong to this user");
        }

        final Transaction createdTransaction = Transaction.builder()
                .userId(userId)
                .accountId(account.getId())
                .categoryId(category.getId())
                .amount(input.getAmount())
                .description(input.getDescription())
                .createdAt(Instant.now())
                .build();

        final Transaction saved = transactionRepository.save(createdTransaction);
        BigDecimal balanceChanged=getBalanceChange(saved.getAmount(),category);
        accountService.changeBalance(saved.getAccountId(), balanceChanged);

        transactionEventProducer.sendTransactionCreated(
                new TransactionCreatedEvent(
                        saved.getId(),
                        saved.getUserId(),
                        saved.getAccountId(),
                        saved.getCategoryId(),
                        saved.getAmount(),
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

    public TransactionDto getTransactionById(Long id) {
        return transactionRepository.findById(id)
                .map(this::toDto)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction", id));
    }

    @Transactional
    public void updateTransaction(Long id, TransactionDto input) {
        Transaction updatedTransaction = transactionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction", id));

        Long oldAccountId = updatedTransaction.getAccountId();

        accountService.changeBalance(
                oldAccountId,
                getBalanceChangeByCategoryId(
                        updatedTransaction.getAmount(),
                        updatedTransaction.getCategoryId()
                ).negate()
        );
        updatedTransaction.setAccountId(input.getAccountId());
        updatedTransaction.setCategoryId(input.getCategoryId());
        updatedTransaction.setAmount(input.getAmount());
        updatedTransaction.setDescription(input.getDescription());

        accountService.changeBalance(
                updatedTransaction.getAccountId(),
                getBalanceChangeByCategoryId(
                        updatedTransaction.getAmount(),
                        updatedTransaction.getCategoryId()));


        transactionRepository.save(updatedTransaction);
    }

    public void deleteTransaction(Long id) {
        Transaction transaction = transactionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction", id));
        accountService.changeBalance(
                transaction.getAccountId(),
                getBalanceChangeByCategoryId(
                        transaction.getAmount(),
                        transaction.getCategoryId()
                ).negate());
        transactionRepository.delete(transaction);

    }

    public List<TransactionDto> getTransactions(TransactionFilterDto filter){
        if (filter.getUserId() == null) {
            return List.of();
        }
        Specification<Transaction> specification=TransactionSpecification.filter(filter);
        return transactionRepository.findAll(specification)
                .stream()
                .map(this::toDto)
                .toList();
    }
    private BigDecimal getBalanceChange(BigDecimal amount, Category category){
        return switch (category.getType()){
            case INCOME -> amount;
            case EXPENSE -> amount.negate();
        };
    }
    private BigDecimal getBalanceChangeByCategoryId(BigDecimal amount, Long categoryId){
        Category category=categoryRepository.findById(categoryId)
                .orElseThrow(()->new ResourceNotFoundException("Category",categoryId));
        return switch (category.getType()){
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