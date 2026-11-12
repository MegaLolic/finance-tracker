package org.portfolio.financeservice.service;

import org.junit.jupiter.api.Test;
import org.portfolio.financeservice.client.UserClient;
import org.portfolio.financeservice.dto.AccountDto;
import org.portfolio.financeservice.dto.TransactionDto;
import org.portfolio.financeservice.entity.Account;
import org.portfolio.financeservice.entity.Transaction;
import org.portfolio.financeservice.repository.AccountRepository;
import org.portfolio.financeservice.repository.CategoryRepository;
import org.portfolio.financeservice.repository.TransactionRepository;
import org.portfolio.financeservice.event.OutboxService;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StaleVersionTest {
    @Test
    void staleAccountUpdateAndDeleteDoNotChangeTheAccount() {
        var repository = mock(AccountRepository.class);
        var service = new AccountService(repository, mock(UserClient.class),
                mock(TransactionRepository.class), mock(CategoryRepository.class), mock(OutboxService.class));
        var account = Account.builder().id(1L).version(2L).balance(new BigDecimal("100.00")).build();
        when(repository.findByIdAndUserId(1L, 7L)).thenReturn(Optional.of(account));
        when(repository.findForDeletion(1L, 7L)).thenReturn(Optional.of(account));
        var input = AccountDto.builder().version(1L).balance(BigDecimal.ZERO).build();

        assertThrows(ObjectOptimisticLockingFailureException.class,
                () -> service.updateAccount(1L, 7L, input));
        assertThrows(ObjectOptimisticLockingFailureException.class,
                () -> service.deleteAccount(1L, 7L, 1L));
        assertEquals(new BigDecimal("100.00"), account.getBalance());
        verify(repository, never()).saveAndFlush(any());
        verify(repository, never()).delete(any(Account.class));
    }

    @Test
    void staleTransactionUpdateAndDeleteDoNotTouchBalances() {
        var repository = mock(TransactionRepository.class);
        var accounts = mock(AccountRepository.class);
        var categories = mock(CategoryRepository.class);
        var balances = mock(AccountService.class);
        var events = mock(OutboxService.class);
        var service = new TransactionService(repository, accounts, categories,
                mock(UserClient.class), balances, events);
        var transaction = Transaction.builder().id(1L).version(2L).amount(BigDecimal.TEN).build();
        when(repository.findByIdAndUserId(1L, 7L)).thenReturn(Optional.of(transaction));

        assertThrows(ObjectOptimisticLockingFailureException.class,
                () -> service.updateTransaction(1L, 7L, TransactionDto.builder().version(1L).build()));
        assertThrows(ObjectOptimisticLockingFailureException.class,
                () -> service.deleteTransaction(1L, 7L, 1L));
        verifyNoInteractions(accounts, categories, balances, events);
        verify(repository, never()).delete(any(Transaction.class));
    }
}
