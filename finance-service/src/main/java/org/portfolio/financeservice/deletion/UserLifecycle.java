package org.portfolio.financeservice.deletion;

import lombok.RequiredArgsConstructor;
import org.portfolio.financeservice.exception.BusinessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class UserLifecycle {
    private final JdbcTemplate jdbc;

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean lock(Long userId) {
        jdbc.update("INSERT INTO user_lifecycle(user_id) VALUES (?) ON CONFLICT DO NOTHING", userId);
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT deleted FROM user_lifecycle WHERE user_id = ? FOR UPDATE", Boolean.class, userId));
    }

    public void requireActive(Long userId) {
        if (lock(userId)) {
            throw new BusinessException("User financial data has been deleted");
        }
    }
}
