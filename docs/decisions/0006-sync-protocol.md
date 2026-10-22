# 0006: Sync protocol

## Status
Accepted (M1-5). Partly superseded by [0009](0009-anime-id-map.md): the sync API names an anime by `malId` (not the server's `animeId`), and `REJECTED_UNKNOWN_ANIME` no longer exists. The rest still holds.

## Context
The app is offline-first: the local DB is the source of truth and the server is a relay between devices (ADR 0004: the GraphQL schema is the contract). M1-5 defines the server side of sync for `list_entry`; the client loop is M1-6. Facts about the existing data:
- `list_entry.id` is a client-generated UUID, `updated_at` is set by the client, `deleted_at` is a tombstone (Postgres schema v1, M1-3).
- `UNIQUE (user_id, anime_id)`: one entry per anime per user.
- There is no auth yet, so one placeholder user is used.

Checked 2026-10-19 on graphql-kotlin 11.0.0 (graphql-java 26.1) and Apollo Kotlin 5.2.0: graphql-kotlin ships no scalar for `Instant`, `Long` or `UUID`.

## Decision
Use a **server-assigned sequence as the pull cursor and last-write-wins (LWW) on the client's `updatedAt`**.

- **Cursor.** `list_entry.server_seq` (V1) is filled from a Postgres sequence on every insert and every applied update. `changesSince(cursor, limit)` returns rows of the user with `server_seq > cursor`, ordered by `server_seq`. `updated_at` is not usable as a cursor: it is client-set, so a late push with an old timestamp would land behind cursors other devices already hold and never be pulled. **Pushes of one user are serialized** by a transaction-scoped Postgres advisory lock (`pg_advisory_xact_lock`) taken before any sequence value is drawn. Sequence values are assigned before commit, so without the lock a push holding seq 10 could commit after a push holding seq 11; a device that pulled in between would move its cursor past 11 and never see 10. With the lock, commit order equals sequence order for a user. Pulls take no lock. The cursor is an opaque string (today the decimal sequence); a null or empty cursor means from the beginning; an invalid cursor is a GraphQL error.
- **Paging.** `limit` defaults to 500, capped at 1000. The page has `nextCursor` and `hasMore`. The client stores `nextCursor` after applying a page and repeats while `hasMore`.
- **Tombstones.** A deletion is a change with `deletedAt != null`, in the same list and order as other changes. Rows are never hard-deleted.
- **LWW.** A pushed change applies only if its `updatedAt` is strictly newer than the stored row's. Ties and older changes are ignored (`IGNORED_STALE`). A newer change without `deletedAt` on a tombstoned row revives it.
- **Idempotency.** Changes are keyed by the client UUID. Pushing the same batch again, or two overlapping retries, gives `IGNORED_STALE` for each change, writes nothing and does not move the cursor, so clients can retry after a timeout. The per-user lock also makes the existence checks race-free. A batch runs in one transaction, at most 1000 changes.
- **Push result.** One outcome per change, in order: `APPLIED`, `IGNORED_STALE`, `REJECTED_UNKNOWN_ANIME`, `REJECTED_INVALID`, each with `entryId`. For `APPLIED` and `IGNORED_STALE`, `entryId` is the id of the server row the change was applied to or compared with; it equals the change id except when the anime already had a row under another id (below). For the two rejections nothing was written and `entryId` just echoes the change id. There is deliberately no cursor in the push result: the user's newest `server_seq` is not a position this device has pulled up to, and storing it would skip other devices' changes. Only `changesSince.nextCursor` is stored. A client may pull its own changes back; that is harmless.
- **One row per (user, anime).** Two devices adding the same anime offline create two ids for one `(user, anime)` slot, and a re-add after a delete from a device that never had the row does the same. A change whose id differs from the existing row for that anime is merged into that row with the normal last-write-wins rule, whether the row is live or a tombstone: a newer change is `APPLIED` (reviving a tombstone), an older or equal one is `IGNORED_STALE`, and `entryId` is the existing row's id. There is no separate "duplicate" outcome. A retry after a lost response compares equal to the merged row, so it is `IGNORED_STALE` and idempotent. Client rule (M1-6 implements it): when `entryId` differs from the sent id, or a pulled row has the same `(user, anime)` as a local row with another id, rewrite the local id to the server's and keep the local `PENDING` content; last-write-wins then decides the content on the next push or pull. This also covers the local `UNIQUE (user_id, anime_id)`.
- **Scalars.** `Instant` is an ISO-8601 UTC string (server `java.time.Instant`, client `kotlin.time.Instant`), `UUID` a string (client keeps `String`, matching the local TEXT column), `Long` a JSON integer (`anime.id` is BIGINT; GraphQL `Int` is 32-bit). Server hooks: `SyncSchemaHooks`; client: `mapScalar` in `shared/build.gradle.kts` and `InstantAdapter`. The export test uses the same hooks as the runtime.
- **Unknown anime on pull.** A pulled change whose anime is not in the local catalog (FK) is parked in the local `parked_change` table instead of dropped, so the cursor can still advance. Parked changes are retried at the end of every sync; one parked change is kept per anime (the newest), and a change pulled after the anime arrived supersedes it.
- **Auth.** Out of scope. The placeholder user lives in `DevUser`; auth replaces it.

## Consequences
- Clock skew matters: LWW trusts device clocks. A device with a wrong clock can win or lose unfairly. The server rejects (`REJECTED_INVALID`) any change whose `updatedAt` or `deletedAt` is more than 5 minutes ahead of server time, so a bad clock cannot freeze an entry; a device whose clock runs fast gets its edits rejected until the clock is fixed.
- No field-level merge: the whole entry is replaced. Fine while an entry has one editable field (status).
- Pushes of the same user queue behind each other (one lock per user), so a very large batch delays that user's other devices. Batches are capped at 1000 changes.
- The lock only helps if every writer of `list_entry` takes it: `SyncRepository.push` is the only writer today, and a new write path (import, admin tool) must lock the same way, because the `DEFAULT nextval` in V1 would give a lockless insert a sequence value without complaint. It also assumes READ COMMITTED, which is Exposed's default for Postgres; at REPEATABLE READ the transaction would see a stale snapshot after waiting for the lock.
- Timestamps are truncated to milliseconds inside `SyncRepository.push`, so every caller (GraphQL, tests, future code) gets idempotent replays.
- `Long` is sent as a JSON number. That is exact up to 2^53, which covers MAL ids; a future web client (JavaScript numbers) would need `Long` as a string if ids ever grow past that.
- `server_seq` gaps are normal (aborted transactions); clients must not assume contiguity.
- Not verified: Apollo with iOS targets enabled; subscriptions.
- Follow-up: M1-6 (client sync loop), auth replacing `DevUser`.
