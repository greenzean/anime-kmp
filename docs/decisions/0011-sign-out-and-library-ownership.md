# 0011: Sign-out and who owns the library on the device

## Status
Accepted (M2-7); the server call is added by [0012](0012-sign-out-revokes-refresh-token.md)

## Context
D18 and D20: the first login on a device that already has a library merges it into the account, and on logout the app asks whether to keep or delete the library on the device. The local library has no user id ([ADR 0006](0006-sync-protocol.md)): `list_entry` rows carry only `sync_state`, and `sync_cursor` holds one cursor. `server_seq` is one sequence for all users ([ADR 0010](0010-server-sign-in.md)), so a cursor from account A skips rows of account B that have a lower number. There is no server sign-out yet, so refresh tokens stay valid until they expire (30 days).

## Decision
Use **a local-only sign-out, and treat every library on a device as detached from any account until a sign-in pushes it**.

- **Merge:** after sign-in the app runs one sync (push pending rows, pull, last write wins). The result is shown as a summary. If the merge is cancelled or fails, background sync finishes it.
- **Keep:** inside the sync engine's lock, delete synced tombstones, mark every other row `PENDING`, drop the cursor and parked changes, clear the session. The next sign-in, with any account, pushes the whole library and pulls from the start. Synced tombstones go because a deletion the old account already has would delete the same anime from another account.
- **Delete:** inside the same lock, delete all entries, parked changes and the cursor, clear the session. The anime catalog stays.
- **Order:** the library changes first, the session is cleared second. If clearing fails, the user stays signed in with a detached library, and the next sync pushes it again (safe by last write wins). The reverse order would leave a signed-out device with a cursor that belongs to the old account.
- **Session ended by the server** (refresh token rejected): the library is kept and detached the same way, without a question.
- **Before delete**, the app tries one sync and shows how many changes are still only on the device, so the user knows what delete loses.
- Sign-out does not call the server. The refresh token is not revoked.

## Consequences
- Signing in to a different account on a device with a kept library merges the old account's entries into the new one. D18 allows this: the library on the device is the user's.
- A server `signOut` mutation (revoke the token family) is a follow-up ticket. Until then a copied refresh token works for up to 30 days.
- Delete is the only hard delete of `list_entry` rows on the client, plus the removal of synced tombstones on keep.
- Not verified on a device: the session-ended path (a rejected refresh token), which is covered by unit tests only.
