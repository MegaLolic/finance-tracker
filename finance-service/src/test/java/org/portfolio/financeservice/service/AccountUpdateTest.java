package org.portfolio.financeservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.portfolio.financeservice.client.UserClient;
import org.portfolio.financeservice.dto.AccountDto;
import org.portfolio.financeservice.entity.Account;
import org.portfolio.financeservice.entity.CurrencyEnum;
import org.portfolio.financeservice.event.OutboxService;
import org.portfolio.financeservice.exception.BusinessException;
import org.portfolio.financeservice.repository.*;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AccountUpdateTest {
    private AccountRepository accounts;
    private AccountService service;
    private Account account;

    @BeforeEach
    void setUp() {
        accounts = mock(AccountRepository.class);
        service = new AccountService(accounts, mock(UserClient.class), mock(TransactionRepository.class),
                mock(CategoryRepository.class), mock(OutboxService.class));
        account = Account.builder().id(1L).userId(7L).version(2L).name("Old")
                .balance(new BigDecimal("100.00")).currency(CurrencyEnum.BYN).build();
        when(accounts.findByIdAndUserId(1L, 7L)).thenReturn(Optional.of(account));
    }

    private AccountDto input() {
        return AccountDto.builder().version(2L).name("New")
                .balance(new BigDecimal("100.0")).currency(CurrencyEnum.BYN).build();
    }

    @Test
    void renameAcceptsNumericallyEqualBalance() {
        when(accounts.saveAndFlush(account)).thenReturn(account);
        AccountDto result = service.updateAccount(1L, 7L, input());
        assertEquals("New", result.getName());
        assertEquals(new BigDecimal("100.00"), result.getBalance());
        assertEquals(CurrencyEnum.BYN, result.getCurrency());
    }

    @Test
    void balanceChangeDoesNotModifyAccount() {
        AccountDto input = input();
        input.setBalance(BigDecimal.ZERO);
        assertThrows(BusinessException.class, () -> service.updateAccount(1L, 7L, input));
        assertEquals("Old", account.getName());
        assertEquals(new BigDecimal("100.00"), account.getBalance());
        verify(accounts, never()).saveAndFlush(any());
    }

    @Test
    void currencyChangeDoesNotModifyAccount() {
        AccountDto input = input();
        input.setCurrency(java.util.Arrays.stream(CurrencyEnum.values())
                .filter(currency -> currency != CurrencyEnum.BYN).findFirst().orElseThrow());
        assertThrows(BusinessException.class, () -> service.updateAccount(1L, 7L, input));
        assertEquals("Old", account.getName());
        assertEquals(CurrencyEnum.BYN, account.getCurrency());
        verify(accounts, never()).saveAndFlush(any());
    }
}
