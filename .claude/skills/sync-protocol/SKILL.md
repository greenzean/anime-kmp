---
name: sync-protocol
description: |
  Sync protocol between the offline-first client and the server for anime-kmp - pushChanges, changesSince, server_seq cursor, last-write-wins, tombstones, idempotent retries, push outcomes. Use this skill whenever touching sync: the push/pull GraphQL operations, `list_entry` columns that sync, the client sync loop, conflict handling, or adding a synced field or table. Trigger on phrases like "sync", "pushChanges", "changesSince", "cursor", "tombstone", "last-write-wins", "conflict", "server_seq", "pending changes", or "offline sync".
---

# Sync protocol

Decision and rationale: ADR 0006. Contract rules: `graphql-schema` skill. Server code: `server/.../sync/` (rules, repository) and `server/.../graphql/sync/` (types, resolvers). Client operations: `shared/src/commonMain/graphql/PushChanges.graphql`, `ChangesSince.graphql`.

## Model
- Local DB is the source of truth. A local write sets `sync_state = PENDING` and a new `updated_at`. The client pushes pending rows, then pulls.
- Server rows carry `server_seq` (Postgres sequence, bumped on every applied write). It is the only pull cursor. Never use `updated_at` as a cursor: it is client-set.
- Pushes of one user are serialized by an advisory lock taken first in `SyncRepository.push`, so commit order equals `server_seq` order and a pull cursor never skips a row. Keep that lock before any write when changing `push`, and take it in any new code path that writes `list_entry` (the column default `nextval` would not stop a lockless insert). It relies on READ COMMITTED (Exposed's default). Timestamps are truncated to milliseconds inside `push`.

## Push (`pushChanges(changes)`)
- Batch of up to 1000 changes, keyed by client UUID, applied in one transaction.
- Applies a change only if `updatedAt` is strictly newer than the stored one (last-write-wins). Ties and older changes are ignored, which makes retries safe.
- A change with `deletedAt` set is a tombstone. A newer change without it revives the row.
- Per-change outcome, in order: `APPLIED`, `IGNORED_STALE`, `REJECTED_INVALID` (also used for a `malId` of 0 or less, and for `updatedAt`/`deletedAt` more than 5 minutes in the future). The wire names an anime by `malId`; the server's internal anime id never leaves the server (ADR 0009), and an unseen `malId` is accepted and added to the server's ID map. Each result carries `entryId`: the id of the server row the change was applied to or compared with (for a rejection it just echoes the sent id). Client: mark `SYNCED` on `APPLIED` and `IGNORED_STALE` (guard with `updated_at = ?` as `markSynced` does); whenever `entryId` differs from the sent id (the anime already had a row under another id; the change was merged into it by last-write-wins, `APPLIED` or `IGNORED_STALE`), rewrite the local id to `entryId` and keep the local content, then mark `SYNCED` as above; other rejections are bugs to surface, not retry.

- There is no cursor in the push result. Never derive a pull cursor from a push; store only `changesSince.nextCursor`.

## Pull (`changesSince(cursor, limit)`)
- Returns changes (tombstones included) with `server_seq` above the cursor, ordered, plus `nextCursor` and `hasMore`. Null or empty cursor starts from the beginning. Treat the cursor as an opaque string and store it only after the page is applied locally.
- One server row per (user, anime). If a pulled row has the same `(user, anime)` as a local row with a different id (local `UNIQUE (user_id, mal_id)`), rewrite the local id to the pulled one first, then apply the rule below.
- Applying a pulled row locally: insert or replace only if it is newer than the local row or the local row is `SYNCED`; never overwrite a `PENDING` row that is newer.

## Adding a synced field
1. Flyway migration (see `postgres-migrations`), update `ListEntryTable`.
2. Add it to `EntryChange`, the input/output GraphQL types and `SyncRepository` (insert and update).
3. The SDL change goes in the Contract (see `graphql-schema`, Changing the API, and ADR 0015); the `.graphql` operations in `shared` follow it.
4. Add a server test in `SyncApiTest`.

## Accounts on the device (ADR 0011)
- The local library has no user id. Merge on first login is one `SyncEngine.sync()` run. Sign-out is local only.
- Keep: inside `SyncEngine.exclusive`, delete synced tombstones, mark all rows `PENDING`, drop cursor and parked changes (`AccountLibraryDataSource.detachFromAccount`). Delete: remove entries, parked changes and cursor (`clearLibrary`). A session ended by the server detaches the same way.
- Any new code that ends a session or changes the account must detach or clear the library, or the old cursor skips rows of the next account.

## Not yet
Field-level merge, subscriptions, server-side sign-out (revoke).
