package org.portfolio.financeservice;

import org.junit.jupiter.api.Test;
import org.portfolio.financeservice.client.UserClient;
import org.portfolio.financeservice.deletion.UserLifecycle;
import org.portfolio.financeservice.deletion.UserLifecycleAspect;
import org.portfolio.financeservice.event.OutboxService;
import org.portfolio.financeservice.exception.BusinessException;
import org.portfolio.financeservice.repository.*;
import org.portfolio.financeservice.service.AccountService;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserLifecycleTest {
    @Test
    void deletedUserCannotReachFinancialRepositoryThroughServiceProxy() {
        var lifecycle = mock(UserLifecycle.class);
        var accounts = mock(AccountRepository.class);
        var manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        var factory = new AspectJProxyFactory(new AccountService(accounts, mock(UserClient.class),
                mock(TransactionRepository.class), mock(CategoryRepository.class), mock(OutboxService.class)));
        factory.addAspect(new UserLifecycleAspect(lifecycle, new TransactionTemplate(manager)));
        AccountService service = factory.getProxy();
        doThrow(new BusinessException("Deleted")).when(lifecycle).requireActive(1L);

        assertThrows(BusinessException.class, () -> service.getAccountsByUserId(1L));

        verifyNoInteractions(accounts);
        verify(manager).rollback(any());
    }

    @Test
    void activeUserOperationRunsInsideLifecycleTransaction() {
        var lifecycle = mock(UserLifecycle.class);
        var accounts = mock(AccountRepository.class);
        var manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        var factory = new AspectJProxyFactory(new AccountService(accounts, mock(UserClient.class),
                mock(TransactionRepository.class), mock(CategoryRepository.class), mock(OutboxService.class)));
        factory.addAspect(new UserLifecycleAspect(lifecycle, new TransactionTemplate(manager)));
        AccountService service = factory.getProxy();

        service.getAccountsByUserId(1L);

        var order = inOrder(lifecycle, accounts, manager);
        order.verify(lifecycle).requireActive(1L);
        order.verify(accounts).findAccountsByUserId(1L);
        order.verify(manager).commit(any());
    }
}
