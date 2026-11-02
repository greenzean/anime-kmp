# 0019: MAL list import reads the account's list page by page and resumes after a failure

## Status
Accepted (M2-20)

## Context
M2-18 imports a MyAnimeList export file. A user with a MAL account also needs the list pulled from MAL directly, without a file. The import should run after login, and the user should be able to start it again from Settings.

Constraints:
- [ADR 0007](0007-client-side-platform-access.md): only the app talks to MAL. The server never gets MAL data, and the import runs on the device.
- [ADR 0008](0008-mal-api.md): `GET /users/@me/animelist?fields=list_status` works with a login and returns `paging.next`. The throttle is a short-window limit, so calls need spacing and backoff. Paging beyond the first page was not tested in M2-2.
- MAL returns no total count for the list.
- A list can be large. A failed import must not start from the beginning each time.
- [ADR 0017](0017-mal-export-reading-with-xmlutil-and-okio.md) and M2-18: the file import already merges one MAL entry into the library. The MAL list import must use the same rules.
- [ADR 0018](0018-conflicts-stored-locally-user-picks-version.md): when the app and MAL differ, the user decides. The conflict is stored locally.

## Decision
Use **page reads of `GET /users/@me/animelist?fields=list_status` with 100 entries per page, through `MalThrottle`, and the page offset stored locally after each page.**

Requests:
- Page size 100. Requests go through `MalThrottle`: 500 ms between calls, with the backoff from [ADR 0008](0008-mal-api.md) on throttle signals.
- The import runs in `shared/commonMain`, in a coordinator in the MAL domain (`MalImportCoordinator`).

Progress:
- A single row in the table `mal_import_progress` holds the next page offset.
- The row is written after each page is merged.
- The row is deleted when the import completes.
- A failed or interrupted import resumes from the stored offset on the next run.
- MAL gives no total, so the UI shows an indefinite loader with a count of entries done. It does not show a percentage.

When the import runs:
- Automatically after every successful MAL login. `ConnectMalUseCase` starts the coordinator.
- From Settings › MyAnimeList › Import my MAL list.

Merge, per MAL entry, through `MergeMalEntryUseCase`. The same use case is used by the M2-18 file import:

| Case | Result |
|---|---|
| Anime missing locally | Added |
| Differs from the app, or removed in the app | A conflict ([ADR 0018](0018-conflicts-stored-locally-user-picks-version.md)) that the user resolves |
| Same in both | Nothing changes |

Other rules:
- Anime that exist only in the app are left alone. Removing them is for M2-21.
- Imported entries get `list_entry.on_mal = 1`. M2-22 uses this flag to detect removals on MAL.
- The schema change is made in place in the SQLDelight files, with no migration. This follows [ADR 0018](0018-conflicts-stored-locally-user-picks-version.md).
- MAL is called only from the app. The server never talks to MAL ([ADR 0007](0007-client-side-platform-access.md)).

## Consequences
- Easier: a user gets their MAL list on the device after login, without a file. A failed import continues from the stored offset.
- Easier: file import and MAL import share one merge rule, so they cannot drift apart.
- Harder: MAL gives no total, so the loader shows a count and no percentage.
- Risk: the import runs on every login, so a large list keeps the throttle busy for a while.
- Risk: the schema change has no migration. Installs made before this ticket are not migrated, as with [ADR 0018](0018-conflicts-stored-locally-user-picks-version.md).
- Checked on a real account with 277 entries: paging beyond the first page worked, and a failure after page 1 resumed from the stored offset 100. [ADR 0008](0008-mal-api.md) listed this as not tested.
- Not verified: a throttled (429) or blocked (403) answer from the real MAL API; covered by tests only.
- Known: after a resume the count shown starts from the stored offset and the summary counts only the entries merged after the resume.
- Follow-ups: M2-21 removes app-only entries when the user asks. M2-22 uses `on_mal` to detect removals on MAL.
