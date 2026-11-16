package org.portfolio.userservice;

import org.junit.jupiter.api.Test;
import org.portfolio.userservice.deletion.UserDeletionEvent;
import org.portfolio.userservice.deletion.UserDeletionService;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class UserDeletionServiceTest {
    @Test
    void partialAcknowledgementDoesNotRemoveUser() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        var service = new UserDeletionService(jdbc);
        var request = new UserDeletionEvent(UUID.randomUUID(), 1L, "finance");
        when(jdbc.update(contains("completed_at = CURRENT_TIMESTAMP"), eq(1L), eq(request.requestId()))).thenReturn(0);
        service.acknowledge(request);
        verify(jdbc, never()).update(startsWith("DELETE FROM users"), any(Long.class));
    }

    @Test
    void completedRequestRemovesOnlyDeletingUser() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        var service = new UserDeletionService(jdbc);
        var request = new UserDeletionEvent(UUID.randomUUID(), 1L, "analytics");
        when(jdbc.update(contains("completed_at = CURRENT_TIMESTAMP"), eq(1L), eq(request.requestId()))).thenReturn(1);
        service.acknowledge(request);
        verify(jdbc).update("DELETE FROM users WHERE id = ? AND deleting = TRUE", 1L);
    }

    @Test
    void unknownParticipantCannotAcknowledgeDeletion() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        var service = new UserDeletionService(jdbc);
        assertThrows(IllegalArgumentException.class,
                () -> service.acknowledge(new UserDeletionEvent(UUID.randomUUID(), 1L, "unknown")));
        verifyNoInteractions(jdbc);
    }
}
