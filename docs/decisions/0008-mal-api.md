# 0008: MAL API behaviour and how the app uses it

## Status
Accepted (M2-2)

## Context
[ADR 0007](0007-client-side-platform-access.md) moves all MyAnimeList (MAL) access into the app and left several API points unverified. The M2-2 spike tested them on 2026-10-22 with a real client ID (registered app type **Android**, no client secret shown) and curl scripts, API base `https://api.myanimelist.net/v2`. Nothing from the spike is kept in the repo. Facts below were observed with live requests; the last section lists what was not tested.

### Tested facts

| Question | Result |
|---|---|
| Search and details with only `X-MAL-CLIENT-ID` | Work (HTTP 200). `GET /anime?q=&limit=&fields=` returns `data[].node` and `paging.next` (offset based). `GET /anime/{id}?fields=` accepts `synopsis, mean, rank, num_episodes, status, genres, start_date, media_type, main_picture, recommendations`. A missing id returns 404 `{"error":"not_found"}`. |
| `recommendations` field on details | List of 10 entries: `{node:{id,title,main_picture}, num_recommendations}`. About 2.5 KB for One Piece (id 21). |
| `GET /anime/suggestions` (needs a login) | Works with a Bearer token. Same item shape as search, `limit` up to 100 (about 22 KB for 100 with default fields), offset paging. |
| Suggestions for a new user (empty list) | Not tested (see below). |
| Response caching headers | `cache-control: no-store`. No rate-limit headers, no `Retry-After`. |
| Rate limit behaviour | A soft throttle, not a 403. At about 10 requests per second the 5th request got HTTP 307 redirecting to `/error.json` (and every one after it). At about 2 requests per second, 54 requests passed. The next request a few minutes later was 200 again. The 403 block was not triggered. |
| App type at registration | Android app. No client secret is issued. |
| OAuth | Authorization code with PKCE `code_challenge_method=plain` and **no client secret** works. Token endpoint `https://myanimelist.net/v1/oauth2/token`, form encoded. `expires_in` is 2,682,000 s (31 days). |
| Refresh token | `grant_type=refresh_token` with the client ID only works and returns a new access and refresh token. The previous refresh token still worked once after rotation. |
| Login with a Google-based MAL account | Works; the authorize page is on myanimelist.net and the user chose Allow after logging in. |
| List read | `GET /users/@me/animelist?fields=list_status&limit=&status=` returns `data[].{node,list_status}` with `list_status = {status, score, num_episodes_watched, is_rewatching, updated_at}` and `paging.next`. |
| Per-anime list state | `GET /anime/{id}?fields=my_list_status` (with a token). The key is absent when the anime is not on the list. |
| List write | `PATCH /anime/{id}/my_list_status` (form encoded: `status, score, num_watched_episodes, ...`) creates or updates the entry and returns the full status including `updated_at`. Partial updates keep the other fields. Repeating an identical PATCH returns 200 and refreshes `updated_at`. An invalid status returns 400 `bad_request`. |
| List delete | `DELETE /anime/{id}/my_list_status` returns 200 (also when the entry is already gone). |
| Statuses | `watching, completed, on_hold, dropped, plan_to_watch`, matching D6. Score is 0-10, where 0 means unscored. |
| `GET /users/@me` | Returns `id, name, gender, birthday, location, joined_at`. This is platform user data: do not store it or send it to our server (D19). |

## Decision
Use **the endpoints above directly from the app, with a client ID only (no secret)**, and these rules for M2-9, M2-10, M2-16 and M2-21:

- **Search and details need no login.** M2-10 and M2-11 do not depend on M2-9.
- **Fields to request:** search results `id,title,main_picture,mean,media_type,num_episodes,status`; details add `synopsis,genres,start_date,rank,recommendations`; list calls `list_status`. Ask for no more than a screen needs.
- **Caching:** cache details and search results in SQLDelight with an expiry (proposed: 24 hours for details, 1 hour for search), dropped on request (MAL agreement: only as long as reasonably necessary). Never cache the user profile.
- **Throttling and backoff:** limit calls from the client to about 2 requests per second, queue the rest. Treat HTTP 429, 403 and a 307 redirect to `/error.json` as "throttled": stop, back off exponentially (start at 30 s) and surface a typed error. On 403, stop all MAL calls for at least an hour (blocks were reported, not reproduced).
- **Do not follow redirects blindly** on the MAL client: a throttled request is a redirect, and following it hides the signal.
- **OAuth:** PKCE `plain`, client ID from config, no secret. Refresh when the access token is near expiry or on 401; always store the newest refresh token.
- **Conflict design (M2-19):** `list_status.updated_at` is available per entry for both reads and writes.
- **Redirect URI:** the spike used `http://www.redirect.com/anime-kmp`, a domain we do not control, so a third party saw the code in its URL. M2-9 uses an Android App Link on a host the project controls (a static GitHub Pages site serving `/.well-known/assetlinks.json` with the package name and signing certificate SHA-256, plus a static fallback page), so only the verified app opens it. The redirect URL comes from the build property `animekmp.malRedirectUri`. MAL requires `redirect_uri` to match a registered URI exactly and supports only `plain`; register one URI only. Custom schemes were dropped because MAL's acceptance of them is undocumented and any app can claim them.

## Consequences
- M2-9 keeps its plan: no new app type is needed. It needs a redirect URI the app owns.
- M2-9 refreshes a token when it is near expiry and treats any 400 or 401 on the refresh grant as a dead session. Refreshing after a 401 from an API call is added with the first MAL API call (M2-10). If the process is killed while the login page is open, the redirect is dropped and the user starts the login again. The login is cancelled when the app resumes without a redirect, so a browser that closes its tab before handing over the link would make the login fail safe as cancelled; only Chrome-style Custom Tabs on an emulator with Firefox were tried. The authorize URL opens only in a browser that provides Custom Tabs, because with `plain` PKCE the URL carries the verifier.
- M2-10 and M2-11 can proceed without M2-9. M2-16 (suggestions) needs the login (M2-15).
- The throttle is at a short-window rate, so backoff and request spacing are mandatory in M2-10, not an optimization.
- M2-21 may rely on idempotent PATCH and DELETE for retries.
- **Not tested:** the empty suggestions list for a new MAL user (the test account has a list); the length and body of the 403 block; the exact throttle threshold and recovery time; behaviour of rewatch, tags and comments fields; list pagination beyond the first page; access token expiry handling after 31 days.
- Anime content from MAL must still follow the API agreement (see `docs/milestone2-mvp.md`).
