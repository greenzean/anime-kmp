---
name: ktor-server-conventions
description: |
  Conventions for the Ktor server module (`server/`) - package layout, route extensions, env-based config, logging (dev vs JSON prod), serialization, and testApplication tests. Use this skill whenever adding or changing server code: a route, a plugin, a config variable, logging, or a server test. Trigger on phrases like "add a route", "Ktor", "server config", "env var", "health check", "server logging", "logback", "testApplication", or "server module".
---

# Ktor server conventions

The server is a JVM-only Ktor (Netty) app in `server/`. Base package: `com.greenzean.animekmp.server`. Only the server talks to MAL; treat clients as untrusted.

## Layout

```
server/src/main/kotlin/com/greenzean/animekmp/server/
  Application.kt        # main() + Application.module()
  AppConfig.kt          # env-based config
  routes/               # one Route.<feature>Routes() extension per feature
server/src/main/resources/
  logback.xml           # dev
  logback-prod.xml      # prod (JSON)
```

- `main()` reads `AppConfig.fromEnv()` and starts `embeddedServer(Netty, ...)`. `Application.module()` installs plugins and calls each feature's routes inside `routing { }`.
- Each feature exposes `fun Route.xxxRoutes()` in `routes/`; register it in `module()`.
- A file containing a single class is named after it (detekt `MatchingDeclarationName`). Keep `@Serializable` response classes in their own files.

## Config

- Environment only, through `AppConfig.fromEnv(env: Map<String, String> = System.getenv())` so tests pass a fake map.
- Defaults suit local dev. Invalid values fail at startup with a clear message (`error("PORT must be ...")`).
- Every new variable goes into `.env.example` and the README "Run the server" section. Never commit `.env` or secrets.

## Logging

- Logback. `logback.xml` is readable output for dev; `logback-prod.xml` uses `LogstashEncoder` (JSON). Level comes from `LOG_LEVEL`.
- `main()` sets `logback.configurationFile` to `logback-prod.xml` when `APP_ENV=prod`, before any logger is created. Do not touch a logger before that line.
- Do not use `<if>/<then>` in `logback.xml`: with Logback 1.6 it leaves the appenders unattached.
- `CallLogging` is installed in `module()`. Never log tokens, secrets or full request bodies.

## Serialization

`ContentNegotiation { json() }` (kotlinx.serialization). Respond with `call.respond(SomeSerializable(...))`.

## Tests

- `kotlin.test` plus `ktor-server-test-host`:

```kotlin
@Test
fun healthReturnsOk() =
    testApplication {
        application { module() }
        assertEquals(HttpStatusCode.OK, client.get("/health").status)
    }
```

- Config logic is tested as plain unit tests with a fake env map.

## Dependencies

Ktor, Logback and the logstash encoder versions live in `gradle/libs.versions.toml`; `server/build.gradle.kts` references the catalog only.

## Verify

```
./gradlew :server:build   # compile, tests, spotless, detekt
./gradlew :server:run     # then: curl localhost:8080/health
```

## Not decided yet

Database access, GraphQL, auth and the MAL adapter get their own conventions in the tickets that build them; add them here then.
