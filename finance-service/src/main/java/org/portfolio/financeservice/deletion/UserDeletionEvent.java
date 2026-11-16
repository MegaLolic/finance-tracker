package org.portfolio.financeservice.deletion;

import java.util.UUID;

public record UserDeletionEvent(UUID requestId, Long userId, String service) {
    public UserDeletionEvent {
        if (requestId == null || userId == null || userId <= 0) {
            throw new IllegalArgumentException("Deletion request ID and positive user ID are required");
        }
    }
}
