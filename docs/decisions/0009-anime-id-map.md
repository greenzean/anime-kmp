# 0009: Anime references: a server-side ID map keyed by platform IDs

## Status
Accepted (M2-3)

## Context
[ADR 0007](0007-client-side-platform-access.md) and the MVP brief (D2, D19) say our server stores library entries and anime IDs, not anime details. Until now the server `anime` table held `(id, mal_id, title)` and was filled with four dev anime by a seed migration. Entries pointed at `anime.id`, and the sync API sent that internal number as `animeId`. The app had to carry a copy of the same four anime with the same internal numbers (`DevAnimeSeed`) so both sides agreed.

Two ways to reference an anime were compared:

| | Entries store `mal_id` directly | Slim `anime` table as an ID map (chosen) |
|---|---|---|
| Server schema | Drop the `anime` table | Keep `anime(id, mal_id)`, drop `title` |
| Adding another platform later (AniList) | New column on every entry | New column on one small table |
| What the app sends | `malId` | `malId` |

MAL IDs are not a universal ID. AniList publishes the MAL ID of an anime as `idMal`, but the field is optional ([AniList Media reference](https://docs.anilist.co/reference/object/media), checked 2026-10-22), so some AniList entries may have no MAL ID. How often that happens was not verified. The MVP uses MAL only (D16).

## Decision
Use **a server-side ID map that the app never sees**.

- `anime` keeps `id` (internal) and `mal_id` (unique, greater than 0). It has no title or any other detail. Entries still point at `anime.id`.
- The sync API speaks `malId`, never the internal id. On push the server finds the row for that `malId`, or creates it. On pull it looks the `malId` up again.
- The server cannot know MAL's catalog, because it never calls MAL (ADR 0007). So `REJECTED_UNKNOWN_ANIME` is removed, and a `malId` of 0 or less is `REJECTED_INVALID`.
- The server dev catalog is removed. Nothing is deployed yet (first deploy: M2-25), so the five migrations were squashed into one `V1` with the final tables instead of adding an upgrade step. Local databases are recreated once (`docker compose down -v`). After the first deploy, migrations are never edited.
- The app keys its local anime cache and its entries by `mal_id`. Its debug-only dev catalog stays until MAL search exists (M2-11), now with no tie to the server.

## Consequences
- The app no longer needs to learn server numbers, so the two sides can't drift apart.
- Any signed-in client can add rows to the map. Each row is tiny and a push is capped at 1000 changes; M2-24 (server hardening) can add a stricter cap.
- Supporting an anime that has only an AniList ID later means making `mal_id` optional and adding an `anilist_id` column, plus an optional field in the sync API. That is a new ticket.
- The app's local database changes shape with no upgrade script, because the app is not released (M2-27). Test devices clear the app data once. After the first release, a shape change needs a migration.
- [ADR 0006](0006-sync-protocol.md) is partly superseded: the anime reference and the unknown-anime push outcome.
