# 0016: App logging through a small AppLogger interface

## Status
Accepted (M2-32)
Date: 2026-11-02

## Context
The app needs logs for MAL requests, throttling, auth, the local cache and sync. Logs help debugging on the emulator. They must never leak tokens, user data or query text. A logging library could do the routing, but the Android phase needs only Logcat.

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

- `AppLogger` (`debug`, `info`, `warn`, `error`, each with a tag, a message and an optional throwable) lives in `shared/commonMain`. It is the only logging type app code depends on.
- `LogSink` (`minLevel`, `log(level, tag, message, throwable)`) is the plug point.
- `SinkLogger` sends each entry to every `LogSink` in Koin (`getAll<LogSink>()`). It skips a sink below its `minLevel`. A sink that throws never affects the caller or the other sinks.
- A new destination is one class plus `singleOf(::XSink) bind LogSink::class` in Koin.
- `LogcatSink` (`androidMain`) is the only sink now:
  - Debug and up when the app is debuggable (`ApplicationInfo.FLAG_DEBUGGABLE`). `shared` cannot read the app's `BuildConfig`.
  - Warn and up otherwise.
- No Ktor Logging plugin. It logs headers and bodies unless filtered.
- Tags: `MalHttp`, `MalThrottle`, `MalAuth`, `MalCache`, `Sync`.

Rules for what a log line may carry:

- **Allowed:** counts, error codes, paths without the query string, status codes and durations.
- **Never logged:** tokens, the `Authorization` header or any other header, request and response bodies, query strings and search text, auth codes, the MAL username, emails, library contents and titles.
- **HTTP, auth, IO and serialization paths:** log the exception class name only. Do not attach the throwable. Exception messages can contain the URL with its query.
- Each instrumented class has `RecordingLogSink` tests that check the redaction rules.

A remote sink (Datadog or similar) is not part of this decision. Before one is added, its ticket must:

- make it opt-in;
- update the privacy policy (M2-26);
- check again what may be logged.

## Consequences
- Small code that we own. No new dependency and no version lag.
- Redaction is enforced by tests, not by the library. A new log line that breaks the rules fails a test only if a test covers it.
- Logcat output is not shown in release builds below Warn.
- Adding iOS or web output means a new `LogSink`. It may wrap Kermit's writers. Not verified: Kermit's current Kotlin version and its minimum-severity API. This is verified by the ticket that adds the first non-Android sink. No ticket exists yet.
- Not verified: Napier's multi-antilog behaviour and whether the Datadog KMP SDK is still Preview. These matter only if a library is chosen later.
- The `never log` rules also live in the `kmp-data-layer` skill, and `AppLogger` replaces the Kermit mentions in `kmp-error-handling`.
