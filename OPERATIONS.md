# Local infrastructure

Run Java services on Windows (user 8080, finance 8081, analytics 8082), and
PostgreSQL, Kafka, MongoDB and monitoring through Docker Compose.

## Kafka addresses and storage

Windows clients use `localhost:9092`. Docker-network clients use `kafka:29092`.
Kafka UI is at http://localhost:8070 and should report the `local` cluster ONLINE.
The named `kafka-data` volume stores `/tmp/kafka-logs`, including KRaft metadata
and consumer offsets. Do not use `docker compose down -v` unless intentionally
discarding all project databases and Kafka storage.

For an older container without the named volume, save its data **before** the
first recreation. Stop Java producers/consumers first, then:

1. `docker stop finance-kafka`
2. `docker cp finance-kafka:/tmp/kafka-logs ./kafka-backup`
3. Create `finance-assistance_kafka-data` and mount it at `/data` in a temporary
   helper container using the existing Kafka image and a shell entrypoint.
4. Copy the **contents** of `kafka-backup` into `/data` with `docker cp`, then
   `chown -R 1000:1000 /data` (the image's `appuser`). Remove only the helper.
5. `docker compose up -d --no-deps kafka kafka-ui`
6. Verify the old topics and consumer offsets before removing the backup.

If the old broker uses a non-default log directory, copy that directory instead.
The procedure preserves `meta.properties` and its cluster ID; do not format the
restored storage with a different cluster ID. The migration was performed for
the current local installation, with a backup retained outside the repository.

## Metrics

Prometheus: http://localhost:9090/targets. It scrapes host services via
`host.docker.internal:8080`, `:8081`, `:8082`; all three applications must run.
`/actuator/health` and `/actuator/prometheus` are public development endpoints.
Other user/finance endpoints retain JWT authentication. No long-lived JWT is
stored in Prometheus configuration. This compose setup is for a trusted local
environment; use network isolation/authentication before publishing it.

## Failed messages and outbox

See [the delivery contract](finance-service/KAFKA.md) for bounded analytics
retries, `.analytics.DLT` inspection/replay and published-outbox retention.
DLT is not successful MongoDB processing. Check DLT records before their 14-day
retention expires. Fix the cause before replaying, and preserve transaction IDs,
versions and keys. User deletion tombstones must not be removed during routine
cleanup. [User deletion](USER_DELETION.md) still waits for both acknowledgements.

Official references:

- [Kafka broker listeners and log directories](https://kafka.apache.org/40/configuration/broker-configs/)
- [Docker volumes](https://docs.docker.com/engine/storage/volumes/)
- [Spring Kafka error handling and DLT](https://docs.spring.io/spring-kafka/reference/kafka/annotation-error-handling.html)
- [Spring Kafka serializers](https://docs.spring.io/spring-kafka/reference/kafka/serdes.html)
- [Spring Boot Actuator access](https://docs.spring.io/spring-boot/reference/actuator/endpoints.html)
- [PostgreSQL SELECT and SKIP LOCKED](https://www.postgresql.org/docs/16/sql-select.html)
