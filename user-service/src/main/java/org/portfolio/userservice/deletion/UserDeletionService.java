package org.portfolio.userservice.deletion;

import lombok.RequiredArgsConstructor;
import org.portfolio.userservice.exception.UserNotFoundException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserDeletionService {
    private final JdbcTemplate jdbc;

    @Transactional
    public void request(Long userId) {
        var users = jdbc.queryForList("SELECT id FROM users WHERE id = ? FOR UPDATE", Long.class, userId);
        if (users.isEmpty()) {
            Boolean alreadyRequested = jdbc.queryForObject(
                    "SELECT EXISTS(SELECT 1 FROM user_deletion_requests WHERE user_id = ?)", Boolean.class, userId);
            if (Boolean.TRUE.equals(alreadyRequested)) {
                return;
            }
            throw new UserNotFoundException(userId);
        }
        jdbc.update("UPDATE users SET deleting = TRUE WHERE id = ?", userId);
        jdbc.update("""
                INSERT INTO user_deletion_requests(user_id, request_id)
                VALUES (?, ?) ON CONFLICT (user_id) DO NOTHING
                """, userId, UUID.randomUUID());
    }

    @Transactional
    public void acknowledge(UserDeletionEvent event) {
        if (event == null || (!"finance".equals(event.service()) && !"analytics".equals(event.service()))) {
            throw new IllegalArgumentException("Unknown deletion participant");
        }
        // Always lock users before requests, matching request() and avoiding an inverted lock order.
        jdbc.queryForList("SELECT id FROM users WHERE id = ? FOR UPDATE", Long.class, event.userId());
        String column = "finance".equals(event.service()) ? "finance_done" : "analytics_done";
        jdbc.update("UPDATE user_deletion_requests SET " + column + " = TRUE WHERE user_id = ? AND request_id = ?",
                event.userId(), event.requestId());
        int completed = jdbc.update("""
                UPDATE user_deletion_requests SET completed_at = CURRENT_TIMESTAMP
                WHERE user_id = ? AND request_id = ? AND finance_done AND analytics_done AND completed_at IS NULL
                """, event.userId(), event.requestId());
        if (completed == 1) {
            jdbc.update("DELETE FROM users WHERE id = ? AND deleting = TRUE", event.userId());
        }
    }
}
