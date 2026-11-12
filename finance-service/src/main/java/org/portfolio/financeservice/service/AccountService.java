package org.portfolio.financeservice.service;


import org.portfolio.financeservice.client.UserClient;
import org.portfolio.financeservice.dto.AccountDto;
import org.portfolio.financeservice.entity.Account;
import org.portfolio.financeservice.exception.ResourceNotFoundException;
import org.portfolio.financeservice.repository.AccountRepository;
import org.portfolio.financeservice.repository.TransactionRepository;
import org.portfolio.financeservice.repository.CategoryRepository;
import org.portfolio.financeservice.event.OutboxService;
import org.springframework.stereotype.Service;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

@Service
public class AccountService {
    private final AccountRepository accountRepository;
    private final UserClient userClient;
    private final TransactionRepository transactions;
    private final CategoryRepository categories;
    private final OutboxService outbox;

    public AccountService(AccountRepository accountRepository, UserClient userClient,
                          TransactionRepository transactions, CategoryRepository categories, OutboxService outbox) {
        this.accountRepository = accountRepository;
        this.userClient = userClient;
        this.transactions = transactions;
        this.categories = categories;
        this.outbox = outbox;
    }

    public AccountDto createAccount(Long userId, AccountDto input){

        userClient.getUserById(userId);

        final Account createdAccount=Account.builder()
                .name(input.getName())
                .balance(input.getBalance())
                .currency(input.getCurrency())
                .userId(userId)
                .build();
        final Account saved=accountRepository.save(createdAccount);
        return toDto(saved);
    }

    private AccountDto toDto(Account account) {
        return AccountDto.builder()
                .id(account.getId())
                .version(account.getVersion())
                .name(account.getName())
                .balance(account.getBalance())
                .currency(account.getCurrency())
                .build();
    }
    public AccountDto getAccountById(Long id){
        return accountRepository.findById(id)
                .map(this::toDto)
                .orElseThrow(()->new ResourceNotFoundException("Account",id));
    }
    @Transactional
    public AccountDto updateAccount(Long id, Long userId, AccountDto accountDto) {
        Account updatedAccount = accountRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", id));

        if (!Objects.equals(accountDto.getVersion(), updatedAccount.getVersion())) {
            throw new ObjectOptimisticLockingFailureException(Account.class, id);
        }

        updatedAccount.setName(accountDto.getName());
        updatedAccount.setBalance(accountDto.getBalance());
        updatedAccount.setCurrency(accountDto.getCurrency());

        return toDto(accountRepository.saveAndFlush(updatedAccount));// save through JPA(save) and sent changes in DB(flush)
    }
    @Transactional
    public void deleteAccount(Long id, Long userId, Long version) {
        Account account = accountRepository.findForDeletion(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", id));

        if (!Objects.equals(version, account.getVersion())) {
            throw new ObjectOptimisticLockingFailureException(Account.class, id);
        }

        // Lock the parent before listing children: no concurrent FK insert can escape the snapshot.
        for (var transaction : transactions.findAllByAccountIdAndUserIdOrderById(id, userId)) {
            var category = categories.findByIdAndUserId(transaction.getCategoryId(), userId)
                    .orElseThrow(() -> new ResourceNotFoundException("Category", transaction.getCategoryId()));
            outbox.append("DELETED", transaction, category.getType().name());
        }
        accountRepository.delete(account);
    }

    public List<AccountDto> getAccountsByUserId(Long userId){
        return accountRepository.findAccountsByUserId(userId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    public AccountDto getAccountById(Long id, Long userId) {
        return accountRepository.findByIdAndUserId(id, userId)
                .map(this::toDto)
                .orElseThrow(() -> new ResourceNotFoundException("Account", id));
    }

    @Transactional
    public void changeBalance(Long accountId, Long userId, BigDecimal amount) {
        Account account = accountRepository.findByIdAndUserId(accountId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", accountId));

        account.setBalance(account.getBalance().add(amount));
        accountRepository.save(account);
    }
}
