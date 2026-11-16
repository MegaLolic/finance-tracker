CREATE INDEX idx_outbox_published_cleanup ON outbox_events(published_at, sequence)
    WHERE published_at IS NOT NULL;
