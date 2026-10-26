# Data safety notes

Input for the Play Data safety form (M2-27) and the privacy policy (M2-26). Describes what the code does today; hosting details are not decided yet (M2-25).

## What the server stores per user
| Data | Where | Purpose |
|---|---|---|
| Google account ID (`sub`), creation time | `app_user` | Identify the account |
| List entries: anime (MAL ID), status, update time, deletion time | `list_entry` | Sync the library between devices |
| SHA-256 hashes of refresh tokens, family, expiry, revocation time | `refresh_token` | Keep sessions |

No email, name, MAL token or MAL profile data is stored (ADR 0007).

## Account deletion
- In the app: Settings, Delete account, confirm, approve the Google sign-in.
- On the web: `/account/delete` on the server. The user signs in with Google there and confirms. No app is needed.
- `deleteAccount` removes the user, all entries and tombstones, and all sessions in one transaction (ADR 0013). It needs a fresh Google ID token.

## Kept after deletion
- The library copy on the user's device, until the user removes it.
- Server request logs and database backups, until rotated. Retention is not defined yet and must be filled in at M2-25 and M2-26. Logs hold request metadata, not library content.
- Anime ID map rows (`anime`): they hold MAL IDs only and no user data.

## Declare in the form
- Data collected: user ID (Google `sub`) and app activity (library entries), both linked to the account, for app functionality.
- Data is encrypted in transit once the server is behind HTTPS (M2-25).
- Users can request deletion, in the app and on the web page above.
