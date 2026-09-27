package org.portfolio.financeservice.service;


import org.portfolio.financeservice.client.UserClient;
import org.portfolio.financeservice.dto.AccountDto;
import org.portfolio.financeservice.entity.Account;
import org.portfolio.financeservice.exception.ResourceNotFoundException;
import org.portfolio.financeservice.repository.AccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class AccountService {
    private final AccountRepository accountRepository;
    private final UserClient userClient;

    public AccountService(AccountRepository accountRepository, UserClient userClient) {
        this.accountRepository = accountRepository;
        this.userClient = userClient;
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

    public void updateAccount(Long id,AccountDto accountDto){
        Account updatedAccount=accountRepository.findById(id).orElseThrow(()->new ResourceNotFoundException("Account", id));

        updatedAccount.setName(accountDto.getName());
        updatedAccount.setBalance(accountDto.getBalance());
        updatedAccount.setCurrency(accountDto.getCurrency());

        accountRepository.save(updatedAccount);
    }

    public void deleteAccount(Long id) {
        Account account=accountRepository.findById(id).orElseThrow(()->new ResourceNotFoundException("Account", id));
        accountRepository.delete(account);
    }

    public List<AccountDto> getAccountsByUserId(Long userId){
        return accountRepository.findAccountsByUserId(userId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public void changeBalance(Long accountId, BigDecimal amount){
        Account account=accountRepository.findById(accountId)
                .orElseThrow(()->new ResourceNotFoundException("Account",accountId));
        account.setBalance(account.getBalance().add(amount));
        accountRepository.save(account);
    }
}
