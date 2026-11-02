# 0016: App logging through a small AppLogger interface

## Status
Accepted (M2-32)
Date: 2026-11-02

## Context
The app needs logs for MAL requests, throttling, auth, the local cache and sync. Logs help debugging on the emulator. The developer needs full API and database detail to debug. Secrets must never leak. User data must not reach release builds or any remote destination. A logging library could do the routing, but the Android phase needs only Logcat.

Versions and facts were checked by search on 2026-11-02.

| | Own `AppLogger` + `LogSink` | Kermit (Touchlab) | Napier (AAkira) | Datadog KMP SDK |
|---|---|---|---|---|
| Latest checked | Not applicable | 2.2.0 (Maven Central, updated 2026-09-08) | 2.7.1 (Maven Central, 2024-01-04) | `dd-sdk-kotlin-multiplatform-logs` 1.8.0 (Maven Central, updated 2026-08-10) |
| License | Ours | Apache-2.0 | Apache-2.0 | Check before use |
| Targets | Any (`commonMain`) | Android, iOS, JS, WASM | Multiplatform | Android and iOS only, no JS or Wasm |
| Multiple destinations | `LogSink` per class, Koin list | Several `LogWriter`s per logger | Not verified | Remote; no local writer |
| Per-destination level | `minLevel` per sink | `isLoggable` per writer | Not verified | Not applicable |
| Redaction | Our rules, tested | None by default | Not verified | Not applicable |
| Init needs | Koin | Logger setup | Logger setup | Client token and tracking consent at init, native pods on iOS |
| Last stated Kotlin version | Ours | Not verified (configuration page returned 404) | 1.9.21 | Not verified |
| Status | Chosen | Not chosen now | Weakest option | Not chosen; sends data off the device |
| Prototyped | In the M2-32 working tree | Judged from docs and search only | Judged from docs and search only | Judged from docs and search only |

Notes:
- Kermit is the likely route to OSLog and console writers when iOS and web arrive. A `LogSink` can wrap Kermit's writers, so the interface does not change.
- Napier's last release is from January 2024. Its multi-antilog and per-antilog filtering were not verified.
- Neither Kermit nor Napier has a ready Datadog writer. A Datadog sink would be our own bridge either way.
- Not verified: whether the Datadog KMP SDK is still marked Preview, and issue-count trends for all three libraries.

## Decision
Use **our own small logging interface, `AppLogger`, with no library for now**.

- `AppLogger` (`trace`, `debug`, `info`, `warn`, `error`, each with a tag and a message; `error` also takes an optional throwable) lives in `shared/commonMain`. It is the only logging type app code depends on.
- `LogLevel` is `Trace`, `Debug`, `Info`, `Warn`, `Error`. Trace is the lowest level.
- `AppLogger.isLoggable(level)` is true when some sink accepts the level. The inline `trace(tag) { message }` builds its string only when `isLoggable(Trace)` is true.
- `LogSink` (`minLevel`, `log(level, tag, message, throwable)`) is the plug point.
- `SinkLogger` sends each entry to every `LogSink` in Koin (`getAll<LogSink>()`). It skips a sink below its `minLevel`. A sink that throws never affects the caller or the other sinks.
- A new destination is one class plus `singleOf(::XSink) bind LogSink::class` in Koin.
- `LogcatSink` (`androidMain`) is the only sink now:
  - Trace and up when the app is debuggable (`ApplicationInfo.FLAG_DEBUGGABLE`), shown as `Log.v`. `shared` cannot read the app's `BuildConfig`.
  - Warn and up otherwise.
  - Messages longer than Logcat's line limit are split by `chunkForLogcat`.
- No Ktor Logging plugin. We log through our own code, and that code redacts through `LogRedactor`. The Apollo interceptor and the Ktor code log through `LogRedactor`.
- Tags for Debug, Info and Warn summaries: `MalHttp`, `MalThrottle`, `MalAuth`, `MalCache`, `Sync`, `GraphQL`.
- Tags for Trace: `API` (Logcat `AnimeAPI`, for MAL and our GraphQL server) and `SQL` (Logcat `AnimeSQL`). A developer filters one or the other with `adb logcat -s AnimeAPI` or `adb logcat -s AnimeSQL`.
- The old Trace tags `MalHttp`, `GraphQL` and `Sql` are retired for Trace.

### Two tiers

| Tier | Levels | Content |
|---|---|---|
| Strict | Debug, Info, Warn, Error | Counts, error codes, paths without the query string, status codes, durations |
| Trace | Trace (below Debug) | Full detail for debugging, as listed below |

Strict tier rules:

- **Allowed:** counts, error codes, paths without the query string, status codes and durations.
- **Never logged:** tokens, headers, request and response bodies, query strings and search text, auth codes, usernames, emails, library contents and titles.
- **HTTP, auth, IO and serialization paths:** log the exception class name only. Do not attach the throwable. Exception messages can contain the URL with its query.

Trace tier rules:

- **Allowed:** the full URL with its query string, request headers, only the useful response headers (content-type, content-length, retry-after, location, www-authenticate, `x-ratelimit-*`), request and response bodies cut at 8 KB, Apollo variables and responses (including GraphQL error messages), search text, titles, usernames and library data, and every SQL statement with bound values, rows read or changed, duration, transactions (begin, commit, rollback) and notified tables.
- **API output (tag `API`):** each API call is one Trace block with a short id, for example `API #7  MAL  GET /v2/anime  ->  200  (509 ms)`. The block holds the full URL, request headers and a pretty-printed JSON body. Failures show the request and no response. Cache decisions for MAL data (`CACHE hit`, `miss`, `expired`, `stale-fallback`) are logged in the same stream, so a request served from the cache is visible too.
- **SQL output (tag `SQL`):** `LoggingSqlDriver`, a `SqlDriver` wrapper, logs one line per statement. Bound values sit in the statement. `READ` or `WRITE` and the main table come first. Long values and long column lists are shortened. Transactions group as `TX #n begin ... commit (k statements, ms) notified: tables`.
- Redaction is the same in Trace as in the strict tier.
- Trace reaches only sinks whose `minLevel` allows it. `LogcatSink` takes Trace only in debuggable builds. A remote sink sets `minLevel` to Debug or higher and never receives Trace.

Secrets, both tiers:

- `LogRedactor` masks these keys in headers, JSON bodies, form bodies, query strings and Apollo variables: `access_token`, `refresh_token`, `id_token`, `accessToken`, `refreshToken`, `idToken`, `code`, `code_verifier`, `code_challenge`, `state`, `client_secret`. It also masks the `Authorization` and `Cookie` headers and Bearer tokens.
- Masking uses a key deny-list. It can miss a new secret field. A new secret-carrying field must be added to the list and to the tests.
- Each instrumented class has `RecordingLogSink` tests that check the redaction rules.

### Platforms

All logging logic is in `commonMain`: the tiers, the redaction, the SQL driver wrapper, the Ktor and Apollo hooks and the `LogSink` interface. Nothing in `commonMain` touches `android.util.Log`, `NSLog` or the browser console. Only the last-mile writer is per platform:

| Target | Sink | Status |
|---|---|---|
| Android | `LogcatSink` | Now |
| iOS | `OSLogSink` (`iosMain`) | When the iOS target exists |
| Web | `ConsoleLogSink` | When the web target exists |

- Each sink is its own class, registered with `bind LogSink::class` in that platform's Koin module. The sinks are not merged into one.
- Logcat's line limit applies only to `LogcatSink`. iOS and the browser console do not have it.
- Kermit's ready OSLog and console writers can sit behind the iOS and web sinks. Kermit is not used now.

The logs are for the developer to read. They are not sent to users or to any service in this decision.

A remote sink (Datadog or similar) is not part of this decision. Before one is added, its ticket must:

- make it opt-in;
- set its `minLevel` to Debug or higher, so it never receives Trace;
- update the privacy policy (M2-26);
- check again what may be logged.

## Consequences
- Small code that we own. No new dependency and no version lag.
- Redaction is enforced by tests, not by the library. The deny-list can miss a new secret field. A new log line that breaks the rules fails a test only if a test covers it.
- Trace output in debuggable Logcat holds full URLs, request headers, selected response headers, bodies (cut at 8 KB), variables and SQL values, including user data such as search text, titles and library content. It stays on the device.
- Each API call is one block and each SQL statement is one line. Filter by tag (`AnimeAPI` or `AnimeSQL`) to read one stream.
- Logcat output is not shown in release builds below Warn. Trace never reaches a release build.
- Building Trace strings costs time and memory. `isLoggable` and the inline `trace` skip that work when no sink wants Trace.
- Adding iOS or web output means a new `LogSink` (`OSLogSink`, `ConsoleLogSink`). It may wrap Kermit's writers. Not verified: Kermit's current Kotlin version and its minimum-severity API. This is verified by the ticket that adds the first non-Android sink. No ticket exists yet.
- Not verified: Napier's multi-antilog behaviour and whether the Datadog KMP SDK is still Preview. These matter only if a library is chosen later.
- The two-tier rule also lives in the `kmp-data-layer` skill, and `AppLogger` replaces the Kermit mentions in `kmp-error-handling`.
