# 0021: Changes made on MAL are pulled by the app and applied by MAL's update time

## Status
Accepted (M2-22)

## Context
M2-20 imports the MAL list once and M2-21 sends changes made on this device to MAL. A user also changes their list on MyAnimeList's website. Those changes should reach the app, and from the app our server.

Constraints:
- [ADR 0007](0007-client-side-platform-access.md): only the app talks to MAL. The server never gets MAL data or tokens.
- [ADR 0006](0006-sync-protocol.md): the local database is the source of truth. A change applied locally reaches our server through the normal sync.
- [ADR 0018](0018-conflicts-stored-locally-user-picks-version.md): a difference the app cannot settle becomes a conflict and the user picks the version.
- [ADR 0019](0019-mal-list-import.md): the import reads the list page by page and sets `list_entry.on_mal = 1`. It makes a conflict for every difference.
- [ADR 0020](0020-send-changes-to-mal.md): a change not yet sent to MAL is a row in `mal_outbox`. The row is the "pending local change" for MAL. A change that came from MAL must not be queued, or it would be sent back.
- MAL's list endpoint returns a `list_status.updated_at` time for each entry. It was read in third-party client libraries, not in MAL's own reference, and has not been checked against a real response.

## Decision
Use **a repeated pull of the whole MAL list, applied per entry by comparing values, pending state and MAL's `updated_at`.**

When the pull runs:
- On app start, when the last pull is older than 15 minutes.
- Every 6 hours with WorkManager.
- From **Sync now** in Settings › MyAnimeList.
- Not while the M2-20 import is running or has a saved offset. A finished import counts as a pull.

Rules for each entry on MAL:
- No local entry: add it and set `on_mal = 1`.
- Same values: delete any conflict for it.
- Different values and the entry is in `mal_outbox`: save a conflict with MAL's values only if MAL changed since the app last saw it. MAL changed when its values differ from the ones stored in `mal_base`, or when `mal_base` has no row for the entry. If MAL still has the stored values, the entry stays pending and no conflict is made.
- Different values, not in `mal_outbox`, and MAL's `updated_at` is newer than the local `updated_at`: save MAL's values locally and request a sync to our server. The change is not queued for MAL.
- Different values, not in `mal_outbox`, and MAL's `updated_at` is older or missing: keep the local values. Another device's change is still on its way to MAL.

Entries removed on MAL:
- Only after every page was read. If a page fails, nothing is removed.
- An `on_mal` entry that MAL did not return is removed locally and a sync is requested.
- If it is in `mal_outbox`, it becomes a conflict with `removed = true`.

What the app last saw on MAL:
- A table `mal_base` holds one row per anime: the status, progress and score MAL had when the app last observed them.
- It is saved for every item the import or a pull reads, and for every change the sender sends to MAL. It is deleted for an anime removed on MAL and cleared on disconnect.
- It is a three-way merge: the stored values are the common ancestor, the app's values are one side, MAL's values are the other.

Sending:
- Before sending queued changes, the sender runs a pull if the last pull is older than 1 minute. If that pull fails, nothing is sent and the job retries. This stops a change made offline from overwriting a change made on MAL's website.
- The pull can create conflicts, so the sender reads the outbox again after it.

Conflicts and the outbox:
- While an entry has a conflict, the sender skips its outbox row. Nothing is sent to MAL until the user chooses.
- "Keep App" is queued for MAL as before ([ADR 0020](0020-send-changes-to-mal.md)).
- "Keep MAL's version" replaces the waiting outbox row with MAL's values and sends them. This changes a choice into a local change, as the ticket says. It changes ADR 0020, where "Keep Other" was never queued.

State:
- A single-row table `mal_pull` holds `last_pulled_at`. It is set after a complete pull and cleared on disconnect.
- A failed pull leaves `last_pulled_at` as it was and WorkManager retries it.

## Consequences
- Easier: the app needs no change feed from MAL. One walk of the list gives both changes and removals.
- Easier: a change from MAL is never sent back to MAL.
- Harder: every pull reads the whole list. MAL throttles requests, so large lists take several calls through `MalThrottle`. The sender adds one pull, at most once a minute, before it sends.
- Harder: `mal_base` is one more row per anime to keep in step with MAL.
- Harder: a MAL change that arrives while the entry has a pending local change always becomes a conflict, even if the user would have picked the newest one.
- Risk: an entry with no `mal_base` row, such as one added by a MAL file import, becomes a conflict when it has a pending change and differs from MAL.
- Risk: the rule "older or missing `updated_at` keeps the local value" can keep an out-of-date value if MAL does not send `updated_at`. The user can still fix it with a later edit.
- Risk: clocks differ between this device and MAL. A device clock that runs ahead makes local changes look newer than MAL's.
- Verified on the emulator (2026-11-03): `list_status.updated_at` from the real MAL API parses with `Instant.parse`, because a change made on MAL's website was applied by a pull, and that only happens when `updated_at` is present and newer. The official reference page could not be read (it is rendered by JavaScript), so the documented format is not quoted here. A missing or unparseable value counts as not newer.
- Not verified: WorkManager 2.12.0 runs the 6 hour periodic pull under Doze on a real device. To be checked by the M2-22 emulator run.
- Changed by [ADR 0023](0023-mal-connect-import-by-update-time.md): a pull is skipped until one MAL import has finished since MAL was connected.
- Follow-up: devices without MAL are M2-23.
