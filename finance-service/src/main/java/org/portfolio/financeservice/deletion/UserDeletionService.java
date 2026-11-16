package org.portfolio.financeservice.deletion;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserDeletionService {
    private final JdbcTemplate jdbc;
    private final UserLifecycle lifecycle;

    @Transactional
    public void delete(Long userId) {
        lifecycle.lock(userId);
        jdbc.update("UPDATE user_lifecycle SET deleted = TRUE WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM outbox_events WHERE payload ->> 'userId' = ?", userId.toString());
        jdbc.update("DELETE FROM transactions WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM accounts WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM categories WHERE user_id = ?", userId);
    }
}
