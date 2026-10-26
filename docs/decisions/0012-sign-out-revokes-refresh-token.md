# 0012: Sign-out revokes the refresh token

## Status
Accepted (M2-29)

## Context
[ADR 0011](0011-sign-out-and-library-ownership.md) made sign-out local only, so a refresh token copied off the device kept working for its 30 days. [ADR 0010](0010-server-sign-in.md) already stores tokens in families and revokes a family when a rotated token is reused.

## Decision
Use **a `signOut(refreshToken): Boolean` mutation that revokes the whole token family, called by the app after it has signed out locally**.

- The refresh token is the credential, so the mutation needs no bearer token.
- It always returns `true`. A repeated, unknown, already revoked or malformed token is not an error and the result does not reveal whether the token existed.
- Only the family of the presented token is revoked; other devices keep their sessions.
- The app reads the refresh token, runs the keep-or-delete step and clears the session under the sync lock as before, then calls `signOut` and ignores the result. A failed call or no connection still signs the user out locally.

## Consequences
- This replaces the "sign-out does not call the server" rule of ADR 0011 and the "no sign-out yet" note of ADR 0010. The rest of both stays.
- If the call fails, the refresh token stays valid until it expires (30 days). The app does not retry it.
- Still open: signing out of every device, cleaning up expired token rows and rate limiting.
