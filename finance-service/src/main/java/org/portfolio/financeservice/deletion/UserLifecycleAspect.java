package org.portfolio.financeservice.deletion;

import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.portfolio.financeservice.dto.TransactionFilterDto;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/** Serialize financial service operations with deletion, including operations already in flight. */
@Aspect
@Component
@Order(0)
@RequiredArgsConstructor
public class UserLifecycleAspect {
    private final UserLifecycle lifecycle;
    private final TransactionTemplate transactions;

    @Around("execution(public * org.portfolio.financeservice.service.*.*(..))")
    public Object withActiveUser(ProceedingJoinPoint invocation) {
        Long userId = userId(invocation);
        return transactions.execute(status -> {
            lifecycle.requireActive(userId);
            try {
                return invocation.proceed();
            } catch (RuntimeException | Error failure) {
                throw failure;
            } catch (Throwable failure) {
                throw new IllegalStateException("Financial operation failed", failure);
            }
        });
    }

    private Long userId(ProceedingJoinPoint invocation) {
        var method = ((MethodSignature) invocation.getSignature()).getMethod();
        var parameters = method.getParameters();
        var args = invocation.getArgs();
        for (int i = 0; i < parameters.length; i++) {
            if ("userId".equals(parameters[i].getName()) && args[i] instanceof Long id && id > 0) {
                return id;
            }
            if (args[i] instanceof TransactionFilterDto filter && filter.getUserId() != null && filter.getUserId() > 0) {
                return filter.getUserId();
            }
        }
        throw new IllegalArgumentException("Financial operation requires a positive userId");
    }
}
