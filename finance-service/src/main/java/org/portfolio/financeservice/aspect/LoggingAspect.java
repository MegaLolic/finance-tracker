package org.portfolio.financeservice.aspect;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.*;
import org.springframework.stereotype.Component;

@Slf4j
@Aspect
@Component
public class LoggingAspect {
    @Pointcut("execution(* org.portfolio.financeservice.service.*.*(..))")
    public void serviceMethods(){}

    @Before("serviceMethods()")
    public void logBefore(JoinPoint joinPoint){
        log.info("Called service method: {} with args: {}",joinPoint.getSignature().getName(),joinPoint.getArgs());
    }

    @AfterReturning(pointcut= "serviceMethods()",returning="result")
    public void logAfterReturning(JoinPoint joinPoint,Object result){
        log.info("Service method: {}, returned: {}",joinPoint.getSignature().getName(),result);
    }
    @AfterThrowing(pointcut = "serviceMethods()",throwing = "exception")
    public void logAfterThrowing(JoinPoint joinPoint,Exception exception) {
        log.error(
                "Service method: {} failed with exception: {}",
                joinPoint.getSignature().getName(),
                exception.getMessage()
        );
    }
}
