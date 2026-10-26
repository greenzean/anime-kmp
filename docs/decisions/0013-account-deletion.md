# 0013: Account deletion

## Status
Accepted (M2-8)

## Context
Google Play requires apps with accounts to offer deletion in the app and on a public web page, and to declare it in the Data safety form ([policy](https://support.google.com/googleplay/android-developer/answer/13327111)). On 2026-10-26 the policy page was read through a summary only, not verbatim. It says the developer must "provide a web link resource where users can request app account deletion and associated data deletion", and its stated purpose is users who already uninstalled the app. A page that only describes in-app steps is therefore treated as likely to be rejected. This is an inference to re-check by reading the page in a browser at M2-26.

The server stores only the Google `sub`, the synced list entries (tombstones are rows of the same table) and refresh token hashes ([ADR 0010](0010-server-sign-in.md), [ADR 0009](0009-anime-id-map.md)). `list_entry` and `refresh_token` reference `app_user` with `ON DELETE CASCADE`. The server has no email, so a plain web form could not verify who is asking.

## Decision
Use **a `deleteAccount(idToken): Boolean` mutation that needs a fresh Google ID token bound to a single-use delete nonce, used by the app and by a web page with Google sign-in**.

- The mutation takes no bearer token. The ID token is the proof, so the web page (which has no session) and the app use the same call, and an expired access token cannot block a deletion.
- `deleteAccountNonce` issues the nonce. Nonces carry their purpose (`s.` sign-in, `d.` delete) and are consumed only for that purpose, so an ID token made for sign-in cannot delete an account and the reverse. The ID token's `sub` finds the user. If no user exists the call still returns `true`, so a retry after a lost response succeeds.
- Deletion is one `DELETE FROM app_user` in a single transaction. The cascade removes entries, tombstones and every refresh token, so no migration is needed. Tests prove nothing remains for the user and other users are untouched.
- The app clears the session and keeps the local library as an anonymous one (`detachFromAccount`), because the app works without an account.
- `GET /account/delete` serves the explanation as plain HTML and CSS that read without JavaScript, plus `script.js`, which loads Google Identity Services, asks for a delete nonce, shows the Google button, asks for explicit confirmation and calls `deleteAccount`. The page also lists the in-app steps and an `animekmp://delete-account` link.
- The page and its files send a Content-Security-Policy (only self and Google's sign-in script, style and frame), `frame-ancestors 'none'`, `nosniff` and `Referrer-Policy: no-referrer`.
- A deleted user's access token (valid up to 15 minutes) is rejected by `pushChanges` and `changesSince` with `UNAUTHENTICATED`, so the app ends the session instead of retrying.

## Consequences
- The web sign-in needs the web OAuth client to list the page's origin as an authorized JavaScript origin. Google accepts `http://localhost` and HTTPS origins only, so on the LAN box the page can be tried through an SSH tunnel to `localhost`, and real use needs the HTTPS domain from M2-22.
- What the server keeps after deletion is listed in `docs/data-safety.md`. Log and backup retention is not verified: hosting is not set up (M2-22) and the privacy policy (M2-25) must state it.
- The Data safety form is filled in under M2-26.
- Still open: rate limiting on the `/graphql` auth mutations (nonce issuing and token checks predate this ticket), a verified HTTPS App Link instead of the `animekmp://` scheme for this deletion link, which any app can claim (the MAL login redirect already uses one, see [ADR 0008](0008-mal-api.md)), and a global GraphQL exception handler that hides non-auth error text.
