# Transactional outbox and analytics

Financial operations and their events are committed together in PostgreSQL.
`OutboxService` writes a JSON snapshot to `outbox_events` using the transaction's
connection. `OutboxPublisher` polls that table after commit; application events
and the old after-commit Kafka producer have been replaced.

The wire format is `TransactionEvent` in both services: `eventId`, `eventType`
(`CREATED`, `UPDATED`, `DELETED`), `transactionId`, `version`, `userId`,
`accountId`, `categoryId`, `amount`, `description`, `categoryType`, `createdAt`.
Deletion uses the last entity version plus one. All events for a transaction
use its ID as the Kafka key. Deleting an account also queues deletion snapshots
for its cascaded transactions. Only that account-deletion path locks the parent
and children so concurrent inserts/updates cannot escape the deletion snapshots.

## Delivery

Only committed rows are visible to the publisher. One PostgreSQL advisory lock
allows a single relay across application instances. Each polling transaction
selects the oldest unpublished event for each transaction ID; a failed event
blocks later events for that ID, while unrelated transactions can progress.
The relay waits for Kafka acknowledgement before setting `published_at`.

`outbox.poll-interval-ms`, `outbox.batch-size`, `outbox.send-timeout-ms`, and
`outbox.retry-delay-ms` configure polling, batch size, send timeout and retry delay.
Retries continue without an automatic discard limit. Failed sends store
`attempts`, `next_attempt_at` and `last_error` and are logged. Published rows are
retained for inspection; no automatic cleanup is performed, so plan cleanup as
the table grows. Transactions hold relay locks during bounded network waits.

A crash after Kafka accepted a message but before the PostgreSQL acknowledgement
is committed can cause duplicates. This is at-least-once delivery, not exactly
once. `acks=all` and producer idempotence are enabled, but there is one Kafka
replica in the development setup: outbox does not protect against losing broker
storage after an event has been marked published.

Kafka topic retention is seven days with no per-partition byte cap; the previous
80 KiB limit could expire messages even during short analytics outages. KafkaAdmin
updates these topic settings when finance-service starts. Analytics must consume
within retention, or be explicitly rebuilt from PostgreSQL/outbox snapshots.

Useful diagnostics (run against the finance database):

```sql
SELECT sequence, id, transaction_id, event_type, aggregate_version,
       attempts, next_attempt_at, last_error
FROM outbox_events WHERE published_at IS NULL ORDER BY sequence;
```

## Analytics

MongoDB atomically compares versions and replaces the document only for a newer
event. Repeated and older versions are ignored. Deleted transactions remain as
`deleted=true` tombstones so old events cannot resurrect them. Analytics queries
must exclude tombstones; `findAllByUserIdAndDeletedFalse` is supplied for that.

Legacy create events without event metadata are accepted at version -1; legacy
documents without a version are treated as -2. Migration V4 queues current SQL
transactions as versioned snapshots. It cannot identify stale MongoDB documents
for transactions deleted before this change; those require separate reconciliation.
Deploy/restart analytical-service with the new reader before finance-service.

Listener and deserialization failures retry rather than silently skipping records.
A permanently invalid event therefore blocks its partition until corrected;
monitor the listener error logs. A Kafka acknowledgement confirms broker delivery,
not completion of MongoDB processing. Analytics is eventually consistent.

Category creation, defaults, names, deletion behavior and history are unchanged.

## Tests

From `finance-service`, set `FINANCE_TEST_DB_URL` to a dedicated PostgreSQL test
database, optionally set `FINANCE_TEST_DB_USER` and `FINANCE_TEST_DB_PASSWORD`, then:

```powershell
.\mvnw.cmd "-Dtest=VersionApiTest,StaleVersionTest,PersistenceVersionTest,OutboxIntegrationTest" test
```

Tests use temporary schemas and remove them afterward. They cover outbox rollback,
JSON snapshots, migration backfill, retry delay, ordering, concurrent relays and
account cascade deletion. Without the database URL, database tests are skipped.

From `analytical-service`, set `ANALYTICS_TEST_MONGO_URI` to a dedicated test
MongoDB instance and run:

```powershell
.\mvnw.cmd -Dtest=TransactionEventListenerTest test
```

This uses and removes a unique temporary database. It tests duplicates, old events,
deletion tombstones, atomic concurrent updates, legacy compatibility and the Kafka
JSON deserializer. Without the MongoDB URI, these tests are skipped.
