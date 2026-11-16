ALTER TABLE users ADD COLUMN deleting BOOLEAN NOT NULL DEFAULT FALSE;

-- A durable deletion request is also the outbox. It survives removal of the user.
CREATE TABLE user_deletion_requests (
    user_id BIGINT PRIMARY KEY,
    request_id UUID NOT NULL UNIQUE,
    finance_done BOOLEAN NOT NULL DEFAULT FALSE,
    analytics_done BOOLEAN NOT NULL DEFAULT FALSE,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMPTZ
);
