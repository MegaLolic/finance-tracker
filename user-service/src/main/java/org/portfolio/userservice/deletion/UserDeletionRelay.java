package org.portfolio.userservice.deletion;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserDeletionRelay {
    private final JdbcTemplate jdbc;
    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper mapper;
    private final UserDeletionService deletions;

    @Scheduled(fixedDelayString = "${user-deletion.poll-interval-ms:5000}")
    public void publishPending() {
        var requests = jdbc.query("""
                SELECT request_id, user_id FROM user_deletion_requests
                WHERE completed_at IS NULL AND next_attempt_at <= CURRENT_TIMESTAMP
                ORDER BY next_attempt_at LIMIT 20
                """, (row, index) -> new UserDeletionEvent(row.getObject("request_id", UUID.class), row.getLong("user_id"), null));
        for (var request : requests) {
            try {
                kafka.send("user-deletion-requests", request.userId().toString(), mapper.writeValueAsString(request))
                        .get(10, TimeUnit.SECONDS);
                jdbc.update("""
                        UPDATE user_deletion_requests SET next_attempt_at = CURRENT_TIMESTAMP + INTERVAL '30 seconds'
                        WHERE user_id = ?
                        """, request.userId());
            } catch (Exception failure) {
                if (failure instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                    return;
                }
                log.error("Could not publish deletion request {}", request.requestId(), failure);
            }
        }
    }

    @KafkaListener(topics = "user-deletion-completed", groupId = "user-deletion-coordinator")
    public void acknowledge(String json) {
        deletions.acknowledge(mapper.readValue(json, UserDeletionEvent.class));
    }
}
