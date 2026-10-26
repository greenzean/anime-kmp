# 0010: Server sign-in and session tokens

## Status
Accepted (M2-4); sign-out revocation added by [0012](0012-sign-out-revokes-refresh-token.md)

## Context
D24: users log in to our server with Google sign-in only. The app gets a Google ID token (Credential Manager, M2-6) and the server must verify it and issue its own session. The server never calls MAL and stores no platform tokens (ADR 0007), so Google is only an identity check.

Library options for the server side (versions checked on Maven Central, 2026-10-22):

| | `GoogleIdTokenVerifier` (google-api-client) | `java-jwt` 4.6.2 + `jwks-rsa` 0.24.1 (chosen) |
|---|---|---|
| Checks | signature, `aud`, `iss`, `exp` | the same, each one configured explicitly |
| Also covers our session JWTs | no | yes |
| Dependencies | large Google client tree | two small libraries |
| Testing with a fake key set | awkward (fetches Google's keys itself) | a plain `RSAKeyProvider` |

Nothing was run against real Google tokens; the fake-key tests cover every check. The first real token arrives with M2-6.

## Decision
Use **java-jwt and jwks-rsa**, with a server-issued nonce and a rotating refresh token.

- **ID token checks** (`GoogleIdTokenVerifier`): RS256 only (so `alg=none` and HS256 fail), signature from Google's JWKS (cached, rate limited, refetched on an unknown `kid`), `iss` is `accounts.google.com` or `https://accounts.google.com`, `aud` is `GOOGLE_CLIENT_ID` (the web/server client ID), `exp` and `iat` with 30 s leeway, `sub` and `nonce` present.
- **Nonce:** the app calls `signInNonce`, passes the value to Google, then sends the ID token to `signInWithGoogle`. The server requires the token's `nonce` claim to match an unused, unexpired nonce (5 min) and deletes it, so a stolen ID token cannot be replayed. The token is verified before the nonce is consumed, so callers without a valid Google token cannot burn nonces.
- **Users:** `app_user(id UUID, google_sub UNIQUE)`. Keyed on `sub`; no email or other profile data is stored. The id is a UUID because `list_entry.user_id` already is one.
- **Access token:** HS256 JWT signed with `JWT_SECRET` (at least 32 bytes), `sub` = our user id, `iss`/`aud` fixed, 15 min.
- **Refresh token:** 32 random bytes, base64url, 30 days. Only its SHA-256 is stored. `refreshSession` rotates it: the old token is revoked and a successor is stored in the same family. Presenting an already revoked token revokes the whole family.
- **Errors:** every failed check returns the same message with `extensions.code = UNAUTHENTICATED`. The reason goes to the server log, never a token.
- **Config:** `GOOGLE_CLIENT_ID` and `JWT_SECRET` come together or not at all; `APP_ENV=prod` requires them. Without them (dev) the auth mutations return "Sign-in is not configured".
- The schema was added to `V1` instead of a new migration because nothing is deployed yet.

## Consequences
- M2-5 calls `SessionTokens.verifyAccess` to authorize sync and adds the foreign key from `list_entry.user_id` to `app_user`.
- Refresh tokens are one device each; there is no sign-out or "revoke all" yet (M2-7), and expired rows are not cleaned up.
- The unauthenticated mutations have no rate limiting (GraphQL limits are still a TODO).
- Access tokens cannot be revoked before they expire (15 min).
- Real Google tokens are not verified until M2-6.
