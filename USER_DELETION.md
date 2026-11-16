# Asynchronous user deletion

## API and state

Previously `DELETE /users/{id}` immediately removed only the `users` row and returned 204.
It now checks ownership, persists a durable deletion request, marks `users.deleting=true`
in the same PostgreSQL transaction, and returns **202 Accepted**.

202 means queued, not completed. Sign-in and refresh reject deleting users. User-service's
JWT validator also rejects access tokens belonging to deleting or removed users. Updating
a profile uses a row lock and rejects deleting users, so a stale profile update cannot
overwrite the deletion flag. Existing finance access is closed when finance processes
the deletion request, not instantaneously at the HTTP 202 response.

## Protocol

`UserDeletionEvent(requestId, userId, service)` is JSON. Request messages use `service=null`;
acknowledgements use `finance` or `analytics`. Modules have local records with the same
wire contract; no dependency on another service's Java package is introduced.

1. `user_deletion_requests` is a durable work queue/outbox. The scheduled relay polls
   at 5-second intervals and resends outstanding requests approximately every 30 seconds
   to `user-deletion-requests`, keyed by user ID. The request topic is compacted.
2. Finance and analytics use **different consumer groups**; both must receive each request.
3. Finance locks `user_lifecycle`, marks the user deleted, and removes the user's outbox,
   transactions, accounts and categories in one PostgreSQL transaction.
4. Analytics persists a `deleted_users` tombstone before removing `raw_transactions`.
5. Each participant sends `user-deletion-completed` **after** its local cleanup succeeds.
6. User-service records each acknowledgement only for the matching request UUID and user ID.
   Only when both flags are true does it remove the user and mark the request completed.

Sending after commit is intentional: a crash between cleanup and acknowledgement is
repaired by the next repeated request. Cleanup and acknowledgement handling are idempotent.
Kafka acknowledgement is awaited; a failed send cannot be treated as successful completion.
Requests remain in the database if Kafka is unavailable. Completed request metadata and
deletion tombstones are retained to prevent resurrection by late messages.

## Concurrent financial operations

`UserLifecycleAspect` wraps public methods in `financeservice.service` in a
`TransactionTemplate`. It obtains userId from the named method argument, or from the
validated transaction filter, then locks that user's lifecycle row and checks its flag.
Java parameter metadata (`-parameters`, already enabled by the Spring Boot parent) is
required. A financial service method without user context fails closed.

The deletion service is in a separate package and takes the **same lock** before cleanup.
An operation already in progress finishes before cleanup; an operation after cleanup is
rejected with 409. Different users use different rows. Operations for one user, including
reads, are serialized: this is a deliberate correctness-first throughput tradeoff.

The unused `getAccountById(id)` overload without ownership context was removed; the
controller continues to use `getAccountById(id, userId)`.

## MongoDB and crash recovery

The existing standalone MongoDB does not support multi-document transactions. Therefore
the event listener checks the deletion tombstone before writing and again afterwards.
A scheduled cleanup also removes documents for tombstoned users every 30 seconds,
repairing a crash between an upsert and the second check. This provides eventual cleanup,
not a cross-service atomic deletion or a strict guarantee of zero transient documents
while an old operation is in flight. Future analytics reads must exclude tombstoned users.

## Scope and operations

- Apply Flyway migrations `user-service/V2__user_deletion.sql` and
  `finance-service/V5__user_deletion.sql` through normal service startup.
- User-service now needs Kafka; its bootstrap defaults to `localhost:9092` and can be
  overridden with `KAFKA_BOOTSTRAP_SERVERS`.
- Restart all three services after building. Kafka, PostgreSQL and MongoDB must be running
  to complete the workflow. While a participant is offline, the request remains pending.
- Inspect `user_deletion_requests` for progress: `finance_done`, `analytics_done`,
  `completed_at`. A user-facing deletion-progress endpoint is not included.
- Kafka topics are trusted internal channels in the current project. Broker authentication
  and network isolation remain deployment tasks; do not expose deletion topics to clients.
- This deletes application database records and pending finance outbox payloads. It does
  not selectively erase historical Kafka messages, logs or backups. Their retention is a
  separate policy. Existing transactions in Kafka can contain personal descriptions.
- Do not remove tombstones as routine housekeeping or reuse deleted user IDs.

## Checks

Unit/API tests cover 202/ownership, sign-in and refresh rejection, partial/final
acknowledgements, AOP protection of financial repositories, late analytics events,
repeated cleanup, and no acknowledgement on MongoDB failure. Real PostgreSQL lock behavior
and the complete Kafka/PostgreSQL/MongoDB sequence still require an integration run.

For a manual integration check, use a disposable user: create a category, account and
transaction; request deletion; wait for both completion flags; verify all three stores.
Repeat with analytics offline and restart it; the user must remain deleting until its
acknowledgement. Republish an old transaction event and verify it does not restore data.

References:
- [Spring Kafka listeners and consumer groups](https://docs.spring.io/spring-kafka/reference/kafka/receiving-messages/listener-annotation.html)
- [Spring transaction management](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/annotations.html)
- [PostgreSQL row locks](https://www.postgresql.org/docs/16/explicit-locking.html)
