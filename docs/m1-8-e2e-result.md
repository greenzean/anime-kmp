# M1-8: End-to-end check result

Date: 2026-10-20. Environment: Docker Compose (Postgres 18 + server), Android emulator (debug build, `animekmp.serverUrl=http://10.0.2.2:8080/graphql`), macOS. Single emulator; the "second install" was simulated by clearing app data.

## Acceptance

| Item | Result |
|---|---|
| Offline add | Pass. With airplane mode on, adding Steins;Gate shows "Plan to watch · Pending". |
| Reconnect, entry reaches server | Pass. After airplane mode off, the badge became "Synced" and `list_entry` in Postgres had the row (`anime_id 3`, `PLAN_TO_WATCH`, `server_seq 4`). |
| Second install pulls it | Pass (data cleared, not a second device). A fresh launch showed both entries as "Synced". |
| README clone-and-run | Updated (quick start, stale DB note removed, `DATABASE_*` for `:server:run`). Not tested from a fresh clone. |
| Checkpoint: CI, lint, tests on main | See "Checks" below. |

## Findings (follow-up tickets, not fixed here)

1. **Exponential backoff delays sync after failures.** `SyncWorker` returns `Result.retry()`. Earlier runs against a stopped server left the work in exponential backoff (gaps grew to 8+ minutes), so a later reconnect did not sync until the backoff ended, and a forced job run was ignored. Follow-up: #40 (bounded linear backoff).
2. **`ExistingWorkPolicy.KEEP`** drops a sync request made while one is queued or running; not exercised in this run. Follow-up: #40 (`APPEND_OR_REPLACE`).
3. **No manual sync trigger or periodic sync.** Sync runs on app launch and after each add only. Deferred to the backlog (pull-to-refresh plus a periodic worker).
4. **Pulled rows for anime missing from the local catalog are skipped**; the dev catalog is seeded in debug builds only, so release builds would drop pulled entries. The pull cursor still advances past a skipped row, so it is not retried. Follow-up: #41.
5. **Debug cleartext config is gitignored**, so a fresh clone needs the manual setup in the README.
6. Release builds have no cleartext config (HTTPS required), which is intended.

## Not verified

- A real second device or emulator.
- A fresh `git clone` following the README.
- The "add during an in-flight sync" case.
