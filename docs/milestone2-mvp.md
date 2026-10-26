# Milestone 2: MVP

Agreed on 2026-10-20. This defines what the first real release of the app is. M0 and M1 built a walking skeleton (local library, sync to our server, a hardcoded dev user and dev catalog); M2 turns it into the product. Ideas left out of the MVP are in [`future-features.md`](future-features.md). The architecture change is recorded in [ADR 0007](decisions/0007-client-side-platform-access.md).

## Terms

- **Server**: the Ktor/Postgres server in `server/`.
- **Platforms**: anime websites outside our server, such as MyAnimeList (MAL) and AniList, and their export files.
- **Device**: one install of the app.
- **Library**: the user's list of anime, with status, episode progress and score per entry.

## What the app is

An app to keep track of the anime you watched, are watching and want to watch. It works offline and without an account. It finds new anime and personal suggestions through MAL. It imports your list from MAL, keeps MAL updated, and exports to a file. An optional account on our server keeps the library the same on all your devices.

## Decisions

### How the app and server work
- **D1.** The app works fully without an account. All data lives on the device. MAL login, import, export and MAL sync all work without our server.
- **D2.** Our server exists only to sync the library between the user's own devices. It stores the account, the library entries and anime IDs, not anime details (titles, images, synopsis).
- **D3.** Our server never sees platform passwords or tokens. The user logs in to MAL on each device separately.
- **D4.** Only the app talks to MAL; our server never does (ADR 0007).
- **D5.** Each platform is used as far as its API allows.

### Library
- **D6.** Statuses are MAL's five: Watching, Completed, On hold, Dropped, Plan to watch.
- **D7.** Each entry has status, episode progress and score (1–10).
- **D8.** Anime only; no manga.
- **D25.** Titles are shown in English by default, falling back to MAL's main title. A Settings option switches to the main title. (Added 2026-10-26.)
- **D26.** Status follows progress like on MAL: starting a Plan to watch entry sets Watching, reaching the last episode sets Completed, and choosing Completed fills progress to the total. (Added 2026-10-26.)

### Search
- **D9.** Library search runs on the device and works offline: search by title, filter by status, sort by title, score or last updated.
- **D10.** Search for new anime uses MAL and needs internet. Offline, the app says that searching for new anime needs a connection.

### Recommendations
- **D11.** Getting recommendations means MAL's personal suggestions (`GET /anime/suggestions`). They need a MAL login on that device. Without a login, or when MAL has nothing to suggest yet (new MAL users get an empty list), the screen explains why and shows nothing.
- **D12.** Giving a recommendation means rating the anime (the score in D7), which is sent to MAL.

### MAL import, export and sync
- **D13.** When the user is logged in to MAL, every change made in the app is sent to MAL automatically. This also keeps MAL's suggestions up to date.
- **D14.** MAL sync is two-way and automatic: changes made on MAL's website also come into the app.
- **D15.** **User decides** (a feature of its own): when the app and MAL disagree about the same anime, during sync or import, the user chooses which version to keep. It also covers MAL being linked on one device but not another: the device without MAL asks the user what to do.
- **D16.** MAL is the only platform in the MVP, by two routes: MAL login (import and sync through the API) and manual files (import a MAL export file, export the library to a file).
- **D23.** Manual export writes a MAL-format file only.
- **D27.** Connecting MAL on a device merges both ways automatically: the MAL list is imported (differences become choices for the user, D15), and anime that exist only in the app are added to MAL. (Added 2026-10-26.)

### Several devices and the account
- **D17.** Only the device where a change was made sends it to MAL. Other devices get it through our server and do not send it again. This is strict: a change made on a device without MAL never reaches MAL, and that device's prompt (D15) says so.
- **D18.** On the first login on a device that already has a library, its entries are merged into the account automatically.
- **D19.** For linked platforms, the server stores only that a platform is linked (for example "MAL"), with no platform username or other platform user data.
- **D20.** On logout, the app asks whether to keep or delete the library on the device.
- **D24.** Users log in to our server with Google sign-in only. The server verifies the Google ID token and issues its own session token.

### Release
- **D21.** The MVP is Android only; iOS and web come later.
- **D22.** The MVP includes putting the server online and publishing the app on Google Play. That brings in hosting, a privacy policy and account deletion (see below).

## MAL API findings (checked 2026-10-20, tested 2026-10-22)

Sources: [API v2 reference](https://myanimelist.net/apiconfig/references/api/v2), [authorization](https://myanimelist.net/apiconfig/references/authorization), [API license and developer agreement](https://myanimelist.net/static/apiagreement.html). The points below were tested live in the M2-2 spike; details and what was not tested are in [ADR 0008](decisions/0008-mal-api.md).

- **Personal suggestions:** `GET /anime/suggestions` returns suggestions for the logged-in user (up to 100 per page). The empty list for a new user was *not tested* (the test account had a list).
- **Search and details without a user login:** work with only the `X-MAL-CLIENT-ID` header. Confirmed.
- **Per-anime recommendations:** a `recommendations` field on anime details, 10 entries of `{node, num_recommendations}`, about 2.5 KB. Confirmed.
- **Rate limits:** not published. Tested: about 10 requests per second gets a 307 redirect to `/error.json` from the 5th request; about 2 per second passed 54 requests. Recovery took minutes. The 403 block (reported unofficially as 1–2 hours) was not triggered. Space requests, cache, and back off.
- **OAuth:** authorization code with PKCE, `plain` method only, no client secret: confirmed. MAL offers an *Android app* type at registration, with no secret. Refresh tokens work with the client ID only.
- **List:** read with `list_status` (including `updated_at`) and write with `PATCH`/`DELETE` on `/anime/{id}/my_list_status`: confirmed.
- **Agreement** (summary, not legal advice):
  - Client-side caching only as long as reasonably necessary.
  - Remove content within 24 hours on request.
  - No revenue without written authorization.
  - Do not modify MAL content.
  - No scraping.
  - Do not store user credentials.
  - A privacy policy and user consent are required.
  - Server-side storage of personal information is restricted (one reason for D19).

## Google sign-in and Play

Checked 2026-10-20.

- **Account deletion is MVP scope.** Apps that let users create an account must offer deletion both in the app and through a web page outside the app, and declare it in the Play Console's Data safety form. Data kept for security or legal reasons must be disclosed. Done in M2-8: [ADR 0013](decisions/0013-account-deletion.md), [data-safety.md](data-safety.md). ([Play policy](https://support.google.com/googleplay/android-developer/answer/13327111))
- **Google sign-in on Android** uses Credential Manager (`androidx.credentials` 1.6.0 stable) with the Sign in with Google library (`googleid` 1.2.1), which returns a Google ID token. Legacy `GoogleSignInClient` (`play-services-auth`) is deprecated. ([guide](https://developer.android.com/identity/sign-in/credential-manager-siwg), [migration](https://developer.android.com/identity/sign-in/legacy-gsi-migration)) Rechecked on Google Maven on 2026-10-22 (M2-6): `credentials` and `credentials-play-services-auth` 1.6.0 are the latest stable (1.7.0-alpha03 exists), `googleid` 1.2.1. M2-6 uses `GetSignInWithGoogleOption` (the explicit button flow) with the web client ID and the server nonce.
- **Server-side check:** verify the ID token with a library (Google names `GoogleIdTokenVerifier` from google-api-client for Java; a general JWT library is also acceptable). Check the signature against Google's rotating keys, `aud` (the web/server client ID passed to `setServerClientId`), `iss` (`accounts.google.com` or `https://accounts.google.com`), `exp` and the nonce if one is set. Key users on `sub`, not email. ([guide](https://developers.google.com/identity/gsi/web/guides/verify-google-id-token))
- **iOS later:** App Store guideline 4.8 requires an equivalent privacy-focused login (in practice Sign in with Apple) next to Google sign-in. ([guidelines](https://developer.apple.com/app-store/review/guidelines/))

## Not in the MVP

Manga, streaming links, new-episode notifications, social feed and comments, web client. See [`future-features.md`](future-features.md).
