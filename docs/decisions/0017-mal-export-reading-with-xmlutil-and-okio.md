# 0017: Reading MAL export files with xmlutil and okio, and filling imported anime images

## Status
Accepted (M2-18)
Date: 2026-11-02

## Context
M2-18 imports MyAnimeList's anime-list export (`.xml` or `.xml.gz`) in `shared/commonMain`. The import needs no login. The version catalog had no XML or gzip library.

Versions were checked on 2026-11-02 by Maven metadata and the GitHub API. Project versions: Kotlin 2.4.20, kotlinx-serialization 1.11.0.

| Library | Checked version | Facts |
|---|---|---|
| `io.github.pdvrieze.xmlutil:core` | 1.0.2.1 (newest stable, published 2026-08-25) | Variants: jvm, iosArm64, iosSimulatorArm64, iosX64, js, wasm. Android uses the jvm variant. The README says native is beta quality. Requires kotlin-stdlib 2.4.10 and kotlinx-serialization-core 1.11.0. |
| `com.squareup.okio:okio` | 3.18.2 (Maven lastUpdated 2026-09-04) | Variants listed: jvm and iosArm64. Apollo 5.2.0 already depends on okio 3.16.2 transitively. |

Facts confirmed in the M2-18 working tree:

- The xmlutil reader surfaces CDATA text through its `CDSECT` event.
- `XmlStreaming` is internal in 1.0.2.1. The public entry point is `xmlStreaming`.
- okio `GzipSource` and `GzipSink` compile in `commonMain` and `commonTest`.
- A real MAL `.xml.gz` export (277 entries) and an app-exported `.xml` (2 entries) both imported on the Android emulator. The first import added 276 and kept 1 entry that already existed locally. The second added 277 into an empty library, and all 279 reached the server.

Options for reading the XML:

| | xmlutil core 1.0.2.1 | Hand-written parser |
|---|---|---|
| Prototyped | Yes, in the M2-18 working tree | No |
| Code to maintain | A library dependency | Our own parser code |
| Escapes and CDATA | The library reader surfaces CDATA through its `CDSECT` event | We would have to get escapes and CDATA right |
| Status | Chosen | Rejected: more code to maintain, and escape and CDATA handling to get right |

Other XML or I/O libraries were not researched.

Import creates the anime row itself from the file title. The existing `MalParkedAnimeResolver` only fetched details for anime ids that had parked pulled changes and no local anime row. So an imported anime would never get an image.

## Decision
Use **xmlutil core 1.0.2.1 and okio 3.18.2 as direct `commonMain` dependencies of `shared`.**

- Do not rely on Apollo's transitive okio.
- The parser detects gzip by the magic bytes `0x1f 0x8b`, not by file name. `.xml` and `.xml.gz` both work.
- `MalParkedAnimeResolver` also fetches details for every anime row whose image is missing:
  - A new `animeMissingImage()` on `LibraryLocalDataSource`, backed by the query `selectMissingImage` in `Anime.sq`.
  - The sync engine already runs the resolver on each sync, but only after a push, and a push needs an account.
  - So the import view model runs the resolver itself (`FillImportedImagesUseCase`) after the import ends, with or without an account. It runs even when nothing was added, so importing the same file again finishes any images still missing.
  - The summary is shown first and the images fill in afterwards. If the user leaves the screen, the fetch stops. Images already saved stay, and a later import or a signed-in sync continues.
  - Import also requests a sync after it adds entries.
  - The fetch uses the existing MAL throttle ([ADR 0008](0008-mal-api.md)).

## Consequences
- Positive: imported anime get a title and an image from MAL through the existing throttle, without needing an account. The images arrive over time, one anime at a time.
- Risk: leaving the Settings screen during the fetch stops it. A signed-out user finishes the images by importing the same file again.
- Risk: an anime that MAL has no image for stays without one. It is fetched again on every sync.
- Risk: xmlutil native (iOS) is beta quality.
- Two new dependencies to keep updated: xmlutil and okio.
- Not verified: the iOS build with xmlutil native. No ticket for the iOS phase exists yet (checked in the issue list on 2026-11-02). Re-check when it is created.
- Not verified: whether other XML or I/O libraries would fit better. They were not researched.
- Not verified: the resolved okio version in the full build graph. The decision names 3.18.2 only as the direct dependency.
