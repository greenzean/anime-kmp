---
name: postgres-migrations
description: |
  Postgres schema, Flyway migrations, Exposed tables and the Testcontainers migration test for the Ktor server (`server/`). Use this skill whenever adding or changing a table, column, index or seed data, writing a migration, adding an Exposed table object, or touching database config or DB tests. Trigger on phrases like "migration", "Flyway", "add a column", "new table", "Exposed", "Testcontainers", "seed data", "DATABASE_URL", "tombstone", or "schema change".
---

# Postgres migrations

Decision and versions: ADR 0005. Flyway owns the schema; Exposed only queries.

## Layout

```
server/src/main/resources/db/migration/   V1__create_schema.sql (later: V2__..., V3__...)
server/src/main/kotlin/.../server/db/     Database.kt (pool + Flyway + Exposed), Tables.kt
server/src/test/kotlin/.../server/db/     MigrationTest.kt (Testcontainers)
```

## Rules

- Files are `V<n>__snake_case_description.sql`, numbers consecutive. One concern per migration.
- Nothing is deployed before M2-25, so until the first deploy migrations may be squashed or edited (reset the mini PC database with `server-deploy --reset`). After the first deploy, never edit a migration: fix forward with a new `V<n+1>`.
- No seed data in migrations. The server keeps no anime catalog (ADR 0009); the app seeds its own debug catalog.
- Tables: singular snake_case names, `TIMESTAMPTZ` for times, `UUID` ids for client-created rows (list entries), identity `BIGINT` for server-owned rows (anime), `TEXT` + `CHECK` for enums.
- Sync tables carry `updated_at` and a nullable `deleted_at` tombstone. Rows are soft-deleted, never removed, so pulls can report deletions.
- Every column change in SQL gets the matching change in `db/Tables.kt`. Exposed does not create or alter tables.
- `user_id` has no foreign key until auth adds a users table.

## Config

`AppConfig.database` is read from `DATABASE_URL`, `DATABASE_USER`, `DATABASE_PASSWORD` (see `.env.example`). If `DATABASE_URL` is unset the server starts without a database; if set, the other two are required. `main()` calls `connectDatabase()`, which migrates before serving. `module()` stays database-free so `testApplication` tests need no Postgres.

## Tests

`MigrationTest` starts `postgres:18-alpine` with Testcontainers, runs `connectDatabase()` and asserts on the schema and constraints. It needs Docker, which runs on the mini PC: run it with `server-tests`, never locally; CI runners have Docker. Add a test for each new constraint.

## Verify

```
server-tests :server:test
```

When it passes it deploys the server and the database to the mini PC; check `flyway_schema_history` there.
