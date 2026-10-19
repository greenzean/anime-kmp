# 0005: Postgres access, migrations and test database

## Status
Accepted (M1-3)

## Context
The server needs a first schema (`anime`, `list_entry`), migrations that run on startup, and an integration test that runs in `:server:test` in CI. Versions were checked on Maven Central on 2026-10-19, taking the newest release at least two weeks old.

| Option | Notes |
|---|---|
| Exposed (JetBrains) | Kotlin-native typed DSL, works next to Flyway. 1.x moved packages to `org.jetbrains.exposed.v1.*`. |
| jOOQ | Strongest type safety, but code generation from a migrated database makes the Gradle build heavy. |
| Plain JDBC | No dependency, but boilerplate and no compile-time checks. |

None were prototyped beyond this ticket's schema; the comparison is from docs.

Flyway 13 needs Java 21. CI runs JDK 17, so Flyway stays on 12.x for now.

## Decision
- **Exposed 1.5.0**, DSL only (no DAO), explicit `v1` imports. It queries; it never creates or alters tables.
- **Flyway 12.11.0** (`flyway-core`, `flyway-database-postgresql`) owns the schema. Migrations in `server/src/main/resources/db/migration`, applied when `main()` starts and `DATABASE_URL` is set.
- **HikariCP 7.1.0** pool, **pgjdbc 42.7.13** driver.
- **Testcontainers 2.0.5** (`testcontainers-postgresql`) with `postgres:18-alpine`, matching `compose.yaml`. The test runs in `:server:test`; GitHub's `ubuntu` runners ship Docker.

## Consequences
- Running `:server:test` locally needs Docker.
- Revisit Flyway 13 when CI moves to JDK 21.
- Exposed 1.5.1 and 1.6.0 (newer than the two-week cutoff) were not reviewed. Compatibility with Kotlin 2.4.20 and JDK 25 was not verified separately; the M1-3 build and tests exercise it.
- `ubuntu-latest` moves to Ubuntu 26.04 in November 2026; watch that Docker and Testcontainers still work, or pin `ubuntu-24.04`.
- `list_entry.user_id` is a placeholder with no foreign key until auth adds a users table.
