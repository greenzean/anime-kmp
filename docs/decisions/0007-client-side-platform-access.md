# 0007: Client-side platform access

## Status
Accepted (M2 MVP brief)

## Context
Until M1 the plan was that only the server talks to MyAnimeList, through a MAL adapter in `server/`, with the client treated as untrusted (CLAUDE.md, README). No ADR recorded it, and no adapter was built. The [M2 MVP brief](../milestone2-mvp.md) changes what the server is for:

- The app must work fully without an account on our server, including MAL login, import, export and MAL sync (D1).
- The server exists only to sync a user's library between their own devices (D2).
- The user does not want the server to hold any third-party credentials or platform user data (D3, D19).

The MAL API agreement (read 2026-10-20, [apiagreement](https://myanimelist.net/static/apiagreement.html)) allows client-side storage of MAL content "only for the period of time reasonably necessary", restricts server-side storage of personal information, and forbids storing user credentials. MAL's OAuth supports PKCE for native apps without a client secret (`plain` method only, [authorization](https://myanimelist.net/apiconfig/references/authorization)).

| | Server talks to MAL (previous plan) | App talks to MAL (chosen) |
|---|---|---|
| Works without our account | No | Yes |
| Platform tokens | On the server | Only on each device |
| Anime metadata | Stored on the server | Cached on the device |
| Rate limits | One server, easy to control | Shared by all installs under one client ID |
| Automatic MAL sync | Can run while the app is closed | Runs on the device (background work) |

Judged from docs only; the MAL spike tests the API live.

## Decision
Use **direct platform access from the app**. Our server never calls MAL or any other platform.

- The app calls MAL for search, details, suggestions and list read and write, and caches anime metadata in SQLDelight.
- MAL tokens live in each device's secure storage. The user logs in to MAL on every device that should talk to MAL.
- The server stores the account, library entries, anime IDs and which platforms a user has linked (the platform name only). It stores no anime metadata, tokens, platform usernames or platform user IDs.
- Only the device where a change was made pushes it to MAL (D17).
- The MAL client ID ships in the app and is public. No client secret.
- The previous rule "Only the server talks to MAL" in CLAUDE.md and the README is replaced by this ADR.

## Consequences
- The existing server `anime` table (internal id, `mal_id`, title) and the debug dev catalog no longer fit D2. A follow-up ticket decides whether entries reference `mal_id` directly or the table becomes an ID map, and drops `title` from the server.
- Rate limits are per client ID and unpublished. The app needs caching and backoff on HTTP 403. The MAL spike measures this.
- Automatic MAL sync and two-way pulls (D13, D14) run as device background work, so they depend on the OS scheduler.
- Conflicts between the app and MAL go to the "user decides" feature (D15). That needs its own design.
- The client is still untrusted for our own API. Server-side validation of sync input stays as in ADR 0006.
- Not verified, for the MAL spike: search and details with only the client ID; the `recommendations` field; the suggestions response shape; rate-limit behaviour; MAL's registration app types.
