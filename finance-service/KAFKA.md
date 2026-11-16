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
retained for seven days after publication. `OutboxCleanup` deletes at most 1000
expired rows per hour; unpublished rows are never removed by this job.
Configure `outbox.retention-days`, `outbox.cleanup-batch-size` and
`outbox.cleanup-interval-ms`. The first cleanup waits for one interval. A partial
index on `(published_at, sequence)` and `SKIP LOCKED` keep batches small and allow
multiple instances without waiting on another cleanup. Increase batch size or
frequency if publication exceeds cleanup throughput. This is diagnostic retention,
not a permanent event archive. Transactions hold relay locks during bounded network waits.

A crash after Kafka accepted a message but before the PostgreSQL acknowledgement
is committed can cause duplicates. This is at-least-once delivery, not exactly
once. `acks=all` and producer idempotence are enabled, but there is one Kafka
replica in the development setup: outbox does not protect against losing broker
storage after an event has been marked published.

Kafka topic retention is seven days with no per-partition byte cap; the previous
80 KiB limit could expire messages even during short analytics outages. KafkaAdmin
updates these topic settings when finance-service starts. Analytics must consume
within retention, or be explicitly rebuilt from PostgreSQL and any retained snapshots.

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

Analytics listener and JSON deserialization failures get three retries, one second
apart (`analytics.kafka.retry-attempts`, `analytics.kafka.retry-delay-ms`). After
exhaustion, Spring Kafka's `DeadLetterPublishingRecoverer` sends the record to
`<source>.analytics.DLT`, with original topic/partition/offset and exception headers.
The DLT broker acknowledgement is required before advancing the source offset;
DLT publication failure leaves the source record available for retry. Duplicates
remain possible if the process stops between DLT publication and offset commit.

DLTs for `transaction-events` (or configured `topic.name`) and
`user-deletion-requests` are declared with three partitions and 14-day retention.
Destination partition is selected by Kafka, so source partition count need not
match. The producer handles JSON events, existing acknowledgement strings and raw
bytes from malformed JSON without double encoding. No new library is required.

DLT is quarantine, not successful processing: during a MongoDB outage otherwise
valid events can also reach it. Monitor these topics in Kafka UI. Correct the
root cause and replay corrected JSON to the original topic with the original
transaction ID as key; do not change its version merely to force an update.
There is deliberately no automatic replay loop. Inspect records before replaying
so a poison record does not loop back. Failed deletion requests do not send an
acknowledgement: the durable user-service relay continues resending until cleanup
succeeds. DLT records can contain personal data and expire after 14 days; they are
not selectively erased by user deletion. A Kafka acknowledgement confirms broker
delivery, not completion of MongoDB processing. Analytics is eventually consistent.

## Local Docker and metrics

Java services on Windows use `localhost:9092` (EXTERNAL listener). Docker clients
such as Kafka UI use `kafka:29092` (INTERNAL listener); only port 9092 is published
to Windows. Recreate Kafka and Kafka UI after changing compose configuration.
Kafka currently has no mounted data volume: recreating its container loses its
messages and consumer offsets. Do not recreate an existing broker without saving
its storage first; see `OPERATIONS.md` for the preservation procedure.

Prometheus scrapes `/actuator/prometheus` on host ports 8080/8081/8082. Health and
Prometheus endpoints are accessible without JWT in this local development setup;
business endpoints still require authentication. Run all three Java services for
targets to become UP. Do not expose these development ports to a public network.

References: [Spring Kafka error handling](https://docs.spring.io/spring-kafka/reference/kafka/annotation-error-handling.html),
[serializers and malformed JSON](https://docs.spring.io/spring-kafka/reference/kafka/serdes.html),
[Kafka listener addresses](https://kafka.apache.org/40/configuration/broker-configs/),
[Spring Boot Actuator security](https://docs.spring.io/spring-boot/reference/actuator/endpoints.html).

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
`OutboxIntegrationTest` also checks cleanup batching and preservation of fresh
published rows and old unpublished rows.

From `analytical-service`, set `ANALYTICS_TEST_MONGO_URI` to a dedicated test
MongoDB instance and run:

```powershell
.\mvnw.cmd -Dtest=TransactionEventListenerTest test
```

This uses and removes a unique temporary database. It tests duplicates, old events,
deletion tombstones, atomic concurrent updates, legacy compatibility and the Kafka
JSON deserializer. Without the MongoDB URI, these tests are skipped.

Set `KAFKA_TEST_BOOTSTRAP_SERVERS` to an explicitly selected test broker and run
`./mvnw.cmd -Dtest=KafkaRecoveryIntegrationTest test` from analytical-service.
The test creates/removes unique topics. It checks malformed JSON preservation,
listener-failure quarantine, original-topic headers and continued processing of
the next valid event. Do not use a production broker for test topic creation.
