# 0018: Import conflicts are stored locally and the user picks the version

## Status
Accepted (M2-19). Partly superseded by [ADR 0023](0023-mal-connect-import-by-update-time.md): when the MAL list is imported, differences are settled by MAL's update time. The MAL file import still makes conflicts.

## Context
D15 says the user decides when the app and MAL disagree about the same anime, during sync or import. Until M2-18, file import kept the app's version and dropped the MAL version (`keptYours`, `ImportMalFileUseCase`). The user was never asked.

Constraints:
- D15: no automatic newest-wins. The user chooses.
- [ADR 0006](0006-sync-protocol.md): sync is last write wins with tombstones. Without a stored choice, a sync would overwrite one side silently.
- [ADR 0007](0007-client-side-platform-access.md) and D2, D19: our server stores library entries and anime IDs. It stores no platform data.
- [ADR 0011](0011-sign-out-and-library-ownership.md): a device's library is detached from any account until a sign-in pushes it.

Options:

| Option | Asks the user (D15) | Off our server (ADR 0007) | Outcome |
|---|---|---|---|
| Automatic newest-wins | No | Yes | Rejected by D15 |
| Conflicts stored on our server | Yes | No | Rejected by ADR 0007 |
| Conflicts stored in a local table | Yes | Yes | Chosen |

Judged from the decisions and the M2-18 code. Not prototyped as a separate spike.

## Decision
Use **a local `conflict` table, and let the user keep the app's version or the MAL version.**

What counts as a conflict:
- The same MAL ID exists on both sides, and the two versions differ in status, progress, score or removed state.
- Removed state counts too: removed in the app, or removed on MAL.
- No newest-wins.

The `conflict` table:
- Columns: `mal_id` (primary key), the MAL version's status, progress, score and removed flag, and `created_at`.
- The app's version is not copied. It is read live from `list_entry`.
- The table is local only. It is never sent to our server.
- Conflicts survive an app restart.
- A new import replaces an older conflict for the same anime.
- An import where the two versions now match deletes the stale conflict.
- Clear library (sign-out with delete, ADR 0011) deletes all conflicts.
- Detach from account (sign-out with keep) keeps them. They are about MAL, not about our account.
- Conflicts whose two sides now match are not shown and do not count.

While a conflict waits:
- The entry keeps the app's version.
- The entry syncs normally.

Choices:

| Choice | Effect |
|---|---|
| Keep app | Delete the conflict. The entry does not change. |
| Keep MAL | Write the stored MAL values as an ordinary local change (PENDING, syncs to our server). Delete the conflict. A removed MAL side removes the entry. |
| Keep all app | Keep app for every conflict. No confirmation. |
| Keep all MAL | Ask for confirmation first, because it changes many entries. Then keep MAL for every conflict. |

- Keep MAL writes the stored values as they are. They came from MAL, so the app's entry rules (clamping) do not apply.
- The app shows a neutral banner while conflicts wait. The banner is not an error and cannot be dismissed.
- Import writes its conflicts in one transaction.

Schema:
- The `conflict` table is added in place to the SQLDelight `.sq` file, with no migration. This follows earlier tickets: the app is unreleased, and the schema file is edited and the database regenerated (`databases/1.db`).

## Consequences
- Easier: the user decides, as D15 requires. Conflicts are on the device, so no platform data reaches our server.
- Easier: M2-20 and M2-22 reuse the `conflict` table and the Conflicts screen.
- Harder: the schema change has no migration. Installs made before this ticket are not migrated.
- Known limitation: a removal that is already synced and purged by `deleteSyncedTombstones` leaves no row. A later import then adds that anime as new. It does not raise a conflict. Fixing this means keeping tombstones. Tombstones are out of scope for M2-19. No ticket exists yet for this fix.
- Not verified when this ADR was written: the M2-19 tests and the emulator check (import a differing file, then keep app, keep MAL and keep all, and restart). These are the ticket's Definition of Done and are recorded in its PR.
