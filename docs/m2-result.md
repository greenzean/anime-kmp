# M2-31: End-to-end check of the MVP

Date: 2026-11-12. Commit checked: `185852b` (`main`, after PR #153).

Environment: Android emulator (Pixel 9 API 36 class, 1080x2160), debug build of `main` installed clean, the server the debug build points at (not confirmed to be the production deployment), real MAL account. One emulator only; the second device was simulated.

## Acceptance

| Item | Result |
|---|---|
| No account: search, add, details, status, episodes, score, filter | Pass. Steins;Gate found, added as Plan to watch, details opened, status set to Watching, episodes 1/24, score 9; Watching filter showed it and Completed showed "No results". |
| No account: export and import a file | Pass. Export wrote a MAL XML with the right status, episodes and score ("Exported 1 entry"); importing a 3-entry file said "Added 3 entries". |
| No account: offline | Pass. With wifi and data off, search showed "You're offline. Searching needs a connection. Your library still works." and the library opened. |
| Google sign-in, same library on a second device | Pass with a limit. Sign-in merged the library. A real second device was not available, so I signed out choosing "Delete library from this device" and signed in again: the same entries, statuses, episodes and scores came back from the server. |
| Connect MAL: import | Pass. Connected as the MAL user, imported the whole list (279 entries on MAL, 279 in the app, no conflicts). It took about 3 minutes. |
| MAL: change in the app reaches MAL | Pass. Episodes +1 in the app showed 2 on MAL (public list and the MAL edit page). |
| MAL: change on MAL reaches the app | Pass. Episodes set to 5 on the MAL site; "Sync now" brought 5 into the app. |
| MAL: resolve a conflict | Pass. Importing a MAL file that disagreed with Steins;Gate showed "1 entry needs your choice" and the side by side screen; "Keep app" resolved it and the values stayed (Watching, 5, 9). |
| MAL: second-device prompt | Pass. After sign-in on a device without MAL, the banner "MyAnimeList is linked to your account, but not connected on this device" showed in Library and Settings. |
| Delete the account | Pass. The dialog lists what is deleted and kept, asks to confirm with Google, then the app signed out and kept the local library (279 entries). Server removal observed on a second run on the Play build (finding 8): after clearing the app data and signing in again, the library was empty and MAL was not linked. |
| Privacy policy link | Pass. Opens `greenzean.github.io/privacy/` (HTTP 200); the page matches the app (tokens stay on the device). |
| CI on `main` | Pass. Run 43225819057 (PR #153 merge) succeeded. |
| Lint (`spotlessCheck detekt`) | Pass, run locally. |
| Build and tests (`:androidApp:assembleDebug :shared:allTests :composeApp:testAndroidHostTest`) | Pass, run locally. Deprecation warnings only. |
| Server tests (`:server:test`) | Not run in this ticket; no server change. Covered by CI. |

## Decisions D1-D27

Verdicts come from reading the code and the test names. The tests were not re-run per decision. "Observed" means it was also seen working on the emulator in the manual run above. "Met (code)" means only the code and tests were checked.

| D | Verdict | Evidence |
|---|---|---|
| D1 | Met, observed | Local SQLDelight is the source of truth. The app worked fully with no account and offline; MAL connect, import and sync worked without involving our server. |
| D2 | Met | Server schema stores only `mal_id` and `list_entry` (`V1__create_schema.sql`). |
| D3 | Met | MAL tokens stay on the device (`KeystoreMalTokenStore`); PKCE exchange has no secret. |
| D4 | Met | No MAL call or token in `server/src`. |
| D5 | Not verifiable from code | MAL-only in the MVP, API used per ADR 0008. Needs a check against MAL's docs. |
| D6 | Met | `WatchStatus` has the five MAL statuses; the server has a CHECK. |
| D7 | Met | Score 1-10 and progress rules in `EntryRules`; server CHECK on score. |
| D8 | Met | No manga code or endpoint outside `docs/`. |
| D9 | Met | Search, status filter and sort by title, score, last updated in `LibraryViewModel`. |
| D10 | Met, observed | Offline search showed "You're offline. Searching needs a connection. Your library still works." |
| D11 | Met (code) | Not-connected and empty states in `SuggestionsViewModel` and `ForYouScreen`. The For you tab was not opened in this run, and the empty list for a new MAL user is untested (ADR 0008). |
| D12 | Met, observed | A score set in Details reached MAL (score 9 on the MAL list). |
| D13 | Met, observed | An episode added in the app showed on MAL within 40 seconds. |
| D14 | Met, observed for "Sync now" | A change made on the MAL site came into the app after "Sync now". The automatic periodic pull timing was not observed. |
| D15 | Met, observed | A differing MAL file produced the choice screen; "Keep app" resolved it. The second-device prompt showed. |
| D16 | Met, observed | MAL login import and MAL file import and export all worked. |
| D17 | Met (code) | No queueing when not logged in; pulled changes never queued; app-only entries sent after the first import only. Not exercised on a device without MAL. |
| D18 | Met, observed | The library was merged into the account on sign-in ("Merging your library"). |
| D19 | Met | `linked_platform` stores platform name only. The linked state appeared on a device without MAL. |
| D20 | Met, observed | Sign-out offered Keep or Delete for the library on the device; Delete emptied it, and signing in restored it from the server. |
| D21 | Met | `animekmp.enableIos=false`; no other targets. |
| D22 | Partly verified | Limits, rate limits, HTTPS for release, privacy link and account deletion exist. Observed: the privacy page loads from Settings; account deletion works in the app; the production server answers over HTTPS with 200 on `/health` and on the account-deletion page, its CSS and its script; a signed release build (R8 on) searched MAL, added an anime and signed in with Google against the production server once the upload key was registered (finding 7), and signed in on the build installed from Google Play. On the Play build, MAL connect also worked: the consent page opened, the redirect came back into the app and the import finished ("Connected as ..., Added 193 entries"), so the app link works. Not observed: the Play Console side of the listing (see "Play listing draft"). |
| D23 | Met | Only the MAL XML writer exists. |
| D24 | Met | Google ID token verified on the server; server issues its own session token. |
| D25 | Met | English by default, falls back to the main title; Settings option. |
| D26 | Met | Status follows progress in `EntryRules`. |
| D27 | Met, observed for import | Connecting imported the whole MAL list (279 entries on MAL, 279 in the app). The app-only entries could not be told apart from entries already on MAL, so sending them was not observed. |

## Findings

1. Open bug B-1 (#154): library list or details chips stop drawing until scrolled, on the minified release build in the emulator. Not fixed here. To be reproduced on a real device.
2. KDoc comments remain, against the no-comments rule: `shared/.../auth/domain/GoogleIdTokenProvider.kt` and `server/.../auth/GoogleIdTokenVerifier.kt`. Not touched in this ticket.
3. The privacy page lives in another repository, so this repo cannot check its content.
4. After resolving the only conflict, the Import row in Settings still shows "1 entry needs your choice" while the conflicts row says "Nothing to resolve". The text is stale until the next import. Minor.
5. The MAL list import is slow: about 3 minutes for 279 entries. Progress is shown ("Imported N so far"), so it works, but it is the longest wait in the MVP.
6. The test changed the real MAL list: Steins;Gate is now Watching 5/24, score 9. The other test entries were already on the MAL list (279 entries on both sides), so nothing else was added.

7. Google sign-in does not complete in the release APK signed with the upload key (`./gradlew :androidApp:assembleRelease` with the upload keystore, installed on the emulator). The account picker opens, the chosen account is accepted, and the app stays signed out with no message. The debug build signs in. Cause confirmed: no Android OAuth client in Google Cloud had the upload key's SHA-1. After a new Android client was created for `com.greenzean.animekmp` with that SHA-1 (`39:76:0B:8F:27:C8:B5:D5:6A:0C:18:B5:90:95:71:72:50:25:17:C9`), the same APK signed in ("Signed in with Google"). Google sign-in also works on the build installed from Google Play (version 0.1.0, installer `com.android.vending`, same emulator), so the Play app signing key is registered as well. Separately, a silent failure with no message is a UX gap worth a ticket.

8. Account deletion removes the server data. Checked on the Play build: delete the account (confirmed with Google), clear the app data, sign in again with the same Google account. Result: signed in, "Your library is empty", no "MyAnimeList is linked" banner, even after waiting. The earlier surprise (a fresh Play install that signed in and showed a large library and the MAL banner shortly after my first deletion) is not explained. It may have been an earlier session by the owner on the Play build. It did not repeat.

## Play listing draft (`docs/play-listing.md`)

Checked against the Play build and the release build on the emulator. The Play Console itself (store listing entry, content rating, data safety form, review status) was not visible and was not checked.

| Claim in the draft | Result |
|---|---|
| Works without internet and without an account | Observed |
| Search MyAnimeList and add anime | Observed on the Play build and the release build |
| Track status, episodes and score | Observed |
| Search, filter and sort the library | Filter observed; sort by Score observed (10s first, ties by title) |
| Import and export MyAnimeList files | Observed |
| Optional Google sign-in to sync | Observed on the Play build |
| Optional MAL connect, import, send changes back; tokens stay on the device | Import and send observed; token storage by code only |
| Delete your account and all synced data, in the app or on the web | App side observed. The web page loads (HTTP 200) but was not used to delete. "All synced data": observed in the app (finding 8). |
| Privacy policy link | Observed (opens from Settings, HTTP 200) |

## Not verified

- A real second device. The same-library check used sign-out with "Delete library" and sign-in again on one emulator, as in M1-8.
- D5 (MAL API terms), D11 empty-list case, automatic periodic MAL pull timing, D17 on a device without MAL.
- Deleting an account through the web page.
- The Play Console side of the listing.
- B-1 (#154) on a real device with the release build.
