# 0023: When the MAL list is imported, MAL's update time settles differences instead of asking the user

## Status
Accepted (M2-23)

## Context
[ADR 0018](0018-conflicts-stored-locally-user-picks-version.md) made every difference between the app and MAL a conflict. D15 says the user decides, with no newest-wins. [ADR 0019](0019-mal-list-import.md) reuses that rule for the MAL list import that runs after a login.

On 2026-11-03 a two-device test showed the cost. A user changed an anime on a device before MAL was connected. When MAL connected, the change made a conflict, although MAL's `list_status.updated_at` says which side is newer. The user had to pick the version that was already newer.

[ADR 0021](0021-get-changes-from-mal.md) already uses `updated_at` to apply a change from MAL. The MAL list import can use the same field.

Constraints:
- [ADR 0007](0007-client-side-platform-access.md): only the app talks to MAL. The server never gets MAL data or tokens.
- [ADR 0020](0020-send-changes-to-mal.md): queued changes go to MAL through the outbox. A change that came from MAL must not be queued, or it would be sent back.
- [ADR 0021](0021-get-changes-from-mal.md): a change not yet sent to MAL is a pending change, and a pending change plus a MAL change still makes a conflict.
- [ADR 0022](0022-mal-on-a-second-device.md) and D17 (strict): a device without MAL never sends anything to MAL.
- The MAL file import ([ADR 0017](0017-mal-export-reading-with-xmlutil-and-okio.md)) has no update times. It cannot use this rule.
- MAL's `list_status.updated_at` parses from the real MAL API, as checked on the emulator for [ADR 0021](0021-get-changes-from-mal.md). The official reference page could not be read, so its documented format is not quoted.

## Decision
Use **MAL's `updated_at` to settle differences on every MAL list import. Only the first import after MAL is connected sends a newer local value to MAL. Keep the conflict for the cases where the time cannot decide.**

Rules for each MAL entry on every MAL list import ([ADR 0019](0019-mal-list-import.md)). The import runs on connect (`ConnectMalUseCase` starts it), from Settings › "Import my MAL list", from Retry, and when an import resumes.

- The first import is the import that runs while no import or pull has completed since MAL was connected.
- An interrupted first import that resumes is still the first import.
- The regular pull ([ADR 0021](0021-get-changes-from-mal.md)) does nothing until one import has finished since MAL was connected. The pull needs the last-pulled time, and only a finished import or pull sets it. A failed first import therefore cannot be followed by a pull that marks a pull as done and hides the first import. The user's Retry is the next first import.

| Case | Result |
|---|---|
| Same values in both | Nothing changes. |
| Different, and an unsent change for the anime is in `mal_outbox` | The time rule does not apply. If MAL's values differ from the ones in `mal_base` ([ADR 0021](0021-get-changes-from-mal.md)), it is a conflict. If MAL still has the `mal_base` values, nothing changes and the change stays queued. |
| Different, and MAL's `updated_at` is newer than the local `updated_at` | MAL's values overwrite the local entry. The change is not queued for MAL. A sync to our server is requested. |
| Different, MAL's `updated_at` is readable and not newer than the local `updated_at`, and this is the first import | The local values stay. They are queued for MAL through the outbox ([ADR 0020](0020-send-changes-to-mal.md)). |
| Different, MAL's `updated_at` is readable and not newer than the local `updated_at`, and this is a later import | The local values stay. They are not queued for MAL, as in the regular pull ([ADR 0021](0021-get-changes-from-mal.md)). |
| Different, and MAL's time is missing or unreadable | A conflict, as before ([ADR 0018](0018-conflicts-stored-locally-user-picks-version.md)). |
| The entry was removed locally | A conflict, as before. |

Rules that follow:
- A newer local value is queued for MAL only on the first import after connecting. The device is now connected to MAL, and the user made the change before connecting. A later import keeps a newer local value without queuing it.
- D17 still holds. A device without MAL never sends. A change made on a device without MAL reaches MAL only through a device that connects MAL and imports for the first time, the same way app-only entries already do ([ADR 0020](0020-send-changes-to-mal.md)).
- The MAL file import has no update times. It keeps making conflicts and asking the user (D15).
- A conflict still appears when an unsent change and a MAL change both exist ([ADR 0021](0021-get-changes-from-mal.md)). The time rule does not apply then.
- A conflict resolved as "Keep app" queues the app's value for MAL. It does not come back unless MAL changes again.

## Consequences
- Easier: fewer prompts. A newer change on either side is settled without a question on every MAL list import.
- Harder: until the first import finishes, nothing is pulled. The sender sends queued changes without the pre-send pull in that period, as it does while an import is running ([ADR 0021](0021-get-changes-from-mal.md)).
- Harder: a clock difference between this device and MAL can pick the wrong side. A device clock that runs ahead makes local changes look newer.
- Harder: a user who wanted MAL's older value must change it back in the app or on MAL.
- Harder: a change made on a device without MAL reaches MAL only through the first import after another device connects MAL. A device that is already connected keeps a newer local value on a later import and does not queue it.
- Risk: the rule depends on `list_status.updated_at` from MAL. A missing or unparseable value makes the entry a conflict, as before.
- Verified on two emulators (2026-11-03): a change made before MAL was connected, then connect. There was no conflict, the local value stayed and reached MAL.
- Not verified on a device: a later import that keeps a newer local value without queuing it. Also not verified: a first import that resumes after an interruption.
</content>
