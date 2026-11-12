# Optimistic concurrency

Accounts and transactions have a Hibernate-managed `version`. Flyway migration
V3 initializes existing records to zero. Never set the entity's version from a
request: Hibernate must own it.

GET responses include `version`. PUT requests must send the version returned by
the last GET, alongside the normal editable fields. PUT returns the saved DTO
with its current version. Account updates now return JSON instead of a message.
POST requests do not require a version; a supplied value is not copied to the
new entity. DELETE requires a query parameter, for example:

```http
DELETE /finance/accounts/123?version=2
```

An outdated version or an overlapping database update/delete returns HTTP 409.
A missing PUT/DELETE version returns HTTP 400. On conflict, reload the resource
and review changes before retrying. There are no automatic retries. Conflicts
roll back the entire financial operation, including its balance changes.

Transaction-created events are delivered to Kafka only after the database
transaction commits. Rollback/conflict does not produce an event. This is not a
durable outbox: a crash or Kafka failure after commit can still lose the event;
send failures are logged. Update/delete analytics events are a separate concern.

## Verification

Run `./mvnw test -Dtest=VersionApiTest,StaleVersionTest,TransactionEventProducerTest`
for API validation, stale request checks, and commit/rollback event behavior.
To include real PostgreSQL persistence tests, set `FINANCE_TEST_DB_URL` to a
dedicated test database JDBC URL (never production), optionally set
`FINANCE_TEST_DB_USER` and `FINANCE_TEST_DB_PASSWORD`, and include
`PersistenceVersionTest` in the test selection. It applies migrations in a unique
temporary schema, checks conflicting updates/deletes and rollback of the losing
operation, and drops that schema afterward. Without the URL, this test is skipped.
