# 0022: MAL on a second device: the server keeps only a "MAL linked" flag, and a device without MAL never sends to MAL

## Status
Accepted (M2-23)

## Context
M2-20 to M2-22 connect MAL on one device and keep the list in step with MAL. A user can sign in to the same account on a second device. That device may or may not have MAL connected. The server must know whether the account uses MAL, so the second device can offer to connect it. The server must not learn anything about the MAL account itself.

Constraints:
- [ADR 0007](0007-client-side-platform-access.md): only the app talks to MAL. The server never gets MAL data or tokens, and stores no platform user data.
- [ADR 0006](0006-sync-protocol.md): the local database is the source of truth. A device without MAL still syncs its changes to our server.
- [ADR 0020](0020-send-changes-to-mal.md) and [ADR 0021](0021-get-changes-from-mal.md): only a device with MAL connected sends to MAL and pulls from MAL. Changes pulled from our server are never queued for MAL.
- D17 (strict): a device without MAL never sends anything to MAL.
- D19: the server stores no MAL username and no MAL user ID.
- No library or service version is chosen here, so no version check applies.

## Decision
Use **a per-account "MAL linked" flag on the server, stored as the platform name `MAL` in a linked-platform table, and no MAL data on the server.**

Server:
- A linked-platform table holds one row per account and platform name. The only value is `MAL`.
- The row holds no username, no MAL user ID and no token.
- Connecting MAL on any device sets the flag for the account.
- Disconnecting MAL on one device does not change the flag.
- **Unlink MAL from account** in Settings clears the flag. It does not disconnect MAL on any device.

Settings:
- **Unlink MAL from account** is shown on every device where the account has MAL linked (the flag is set).
- Unlinking clears only the server flag. MAL stays connected on every device that has it connected, and those devices keep sending and pulling as before.

Devices without MAL (strict D17):
- A device without MAL never calls MAL. It never queues changes for MAL and never pulls from MAL.
- Its changes sync to our server only.
- While the flag is set, the prompt to connect MAL says that changes made on this device are saved to the account and are not sent to MAL from this device.
- **Not on this device** is stored on the device and hides that prompt on this device. It does not change the server flag.

## Consequences
- Easier: the server learns one fact per account, that MAL is used. It stores no MAL identity.
- Easier: a device without MAL cannot send anything to MAL. It never sends a change made there. [ADR 0023](0023-mal-connect-import-by-update-time.md) says when such a change reaches MAL.
- Harder: a device without MAL keeps its changes on our server only. MAL gets them only when a device connects MAL and runs its first import ([ADR 0023](0023-mal-connect-import-by-update-time.md)).
- Harder: unlinking does not stop devices that still have MAL connected. Those devices keep sending to MAL until the user disconnects MAL on each of them.
- Risk: a user may expect "Not on this device" to remove the link. It only hides the prompt on that device.
- Risk: the server flag stays set after every device disconnects MAL. Only **Unlink MAL from account** clears it, so the prompt keeps showing on devices without MAL.
- Verified on two emulators (2026-11-03): the two-device flow (flag set on one device, prompt and "Not on this device" on the other, unlink from both).
- Connecting MAL and signing in do not wait for our server. Each sets a "link pending" flag on the device and requests a background sync. The sync links the account and clears the flag. The link is retried after each successful sync until it works.
- "Not on this device" is cleared when the user signs in. A new account or a new sign-in sees the prompt again.
- Connect and sign-in set the "link pending" flag every time, and the background retry links only while the flag is set. A successful unlink clears the flag. The retry decides whether to link under the same lock as link and unlink, so an unlink that runs at the same moment cannot be followed by a stale link.
- The prompt text now says that changes made here are saved to the account and are not sent to MAL from this device.
- Follow-up: none. This closes the device-without-MAL follow-up from [ADR 0021](0021-get-changes-from-mal.md).
