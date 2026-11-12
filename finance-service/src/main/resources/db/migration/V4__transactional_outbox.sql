CREATE TABLE outbox_events (
    sequence BIGSERIAL PRIMARY KEY,
    id UUID NOT NULL UNIQUE,
    transaction_id BIGINT NOT NULL,
    aggregate_version BIGINT NOT NULL,
    event_type VARCHAR(20) NOT NULL,
    payload JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMPTZ,
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_error TEXT
);
CREATE INDEX idx_outbox_pending ON outbox_events(sequence) WHERE published_at IS NULL;
CREATE INDEX idx_outbox_aggregate_pending ON outbox_events(transaction_id, sequence) WHERE published_at IS NULL;

-- Re-publish current rows so existing analytics documents receive versions.
WITH snapshots AS (
    SELECT gen_random_uuid() AS event_id, t.*, c.type AS category_type
    FROM transactions t JOIN categories c ON c.id = t.category_id
)
INSERT INTO outbox_events(id, transaction_id, aggregate_version, event_type, payload)
SELECT event_id, id, version, 'UPDATED', jsonb_build_object(
    'eventId', event_id, 'eventType', 'UPDATED', 'transactionId', id, 'version', version,
    'userId', user_id, 'accountId', account_id, 'categoryId', category_id,
    'categoryType', category_type,
    'amount', amount, 'description', description, 'createdAt', created_at
) FROM snapshots;
