# 0020: Changes made on this device go to MAL through a local outbox, one row per anime

## Status
Accepted (M2-21)

## Context
M2-20 imports the MAL list into the library. M2-21 sends list changes back to MAL. A user changes an anime on the device, and the change should reach their MAL account. Anime that exist only in the app are sent to MAL once after each import.

Constraints:
- [ADR 0007](0007-client-side-platform-access.md): only the app talks to MAL. The server never gets MAL data or tokens.
- [ADR 0008](0008-mal-api.md): `PATCH /anime/{id}/my_list_status` creates or updates an entry, and repeating an identical PATCH is safe. `DELETE /anime/{id}/my_list_status` returns 200, also when the entry is already gone. ADR 0008 says M2-21 may rely on this for retries. The throttle is a short-window limit, so calls go through `MalThrottle` with backoff.
- [ADR 0006](0006-sync-protocol.md): the local database is the source of truth. The UI reads and writes only locally. Sync to our server is a separate path.
- [ADR 0019](0019-mal-list-import.md): an imported entry gets `list_entry.on_mal = 1`. Anime that exist only in the app have `on_mal = 0`. ADR 0019 left them for M2-21; this ADR sends them to MAL instead of removing them.
- A change pulled from our server, or a conflict resolved as "Keep Other", is already the MAL state or comes from another device. Sending it back would write the same value again, or would overwrite MAL with a value the user did not choose on this device.
- Pending sends must survive app restarts and failures, and must not block the UI.

## Decision
Use **a local outbox table in SQLDelight, one row per `mal_id`, holding a snapshot of the list entry, sent by WorkManager through `MalThrottle`.**

Outbox:
- Table `mal_outbox` (in `shared/`, SQLDelight) has one row per `mal_id`. A new change replaces the row, so the latest change wins.
- The row holds a snapshot of the entry: status, score and progress at the time of the change. The send does not replay a list of diffs.
- A row also holds `last_error` and the retry state for the backoff.

What is queued (strict D17):
- Only changes made on this device while connected to MAL are queued.
- Changes pulled from our server are never queued.
- "Keep Other" in a conflict is never queued.
- "Keep App" in a conflict is queued.
- Removals made on this device are queued as a DELETE.

Import and app-only entries:
- After each MAL list import ([ADR 0019](0019-mal-list-import.md)), the app runs one server sync.
- Then the app queues each app-only entry (`on_mal = 0`, not removed) once. These entries are sent as PATCH and then get `on_mal = 1`.

Send rules:
- A successful PATCH sets `on_mal = 1`. A successful DELETE sets `on_mal = 0`.
- Requests go through `MalThrottle` ([ADR 0008](0008-mal-api.md)).
- DELETE with HTTP 404 counts as done. The outbox row is removed.
- PATCH with HTTP 404 drops the change. The outbox row is removed.
- Any other failed send keeps the row, sets `last_error`, and retries with backoff.
- Retries rely on the idempotent PATCH and DELETE of [ADR 0008](0008-mal-api.md).

Scheduling:
- WorkManager runs one one-time job per change.
- A periodic job runs every 6 hours to send rows that are still pending.
- Settings › MyAnimeList has a Retry action that starts the send now.
- Disconnecting MAL clears the outbox.

Scope rules:
- The app calls MAL only from the device. The server gets no outbox data.
- The app does not refresh its token on a 401 from an API call. That is out of scope for M2-21.
- Getting changes from MAL back into the app is M2-22.

## Consequences
- Easier: one row per anime keeps the queue small. Repeated edits to one anime leave one row to send.
- Easier: the snapshot is sent as is. A send does not depend on the order of earlier changes.
- Easier: the rules for pulled changes and "Keep Other" keep MAL from receiving values the user did not choose here.
- Harder: "latest change wins" drops earlier changes for the same anime. Only the newest state reaches MAL.
- Harder: a PATCH 404 drops the change without a retry. A user change is lost if MAL reports the anime as missing.
- Risk: a 401 on an API call is not refreshed in M2-21. It counts as a failed send and stays queued with `last_error`.
- Risk: the retry timing after a failure depends on WorkManager backoff. The 6 hour periodic job is a separate, slower check.
- Not verified: the throttle (429), block (403) and expired-token (401) answers from the real MAL API for PATCH and DELETE. Covered by tests only, to be verified in M2-21.
- Not verified: WorkManager 2.12.0 (the version in `gradle/libs.versions.toml`) behaves as described on a real device, including the 6 hour periodic job under Doze. Not checked with a search for this ADR. Verified by the M2-21 emulator check.
- Follow-ups: M2-22 gets changes from MAL and uses `on_mal` to detect removals on MAL. Refresh on 401 for API calls is a separate ticket.
