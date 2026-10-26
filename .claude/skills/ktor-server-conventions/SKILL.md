---
name: ktor-server-conventions
description: |
  Conventions for the Ktor server module (`server/`) - package layout, route extensions, env-based config, logging (dev vs JSON prod), serialization, and testApplication tests. Use this skill whenever adding or changing server code: a route, a plugin, a config variable, logging, or a server test. Trigger on phrases like "add a route", "Ktor", "server config", "env var", "health check", "server logging", "logback", "testApplication", or "server module".
---

# Ktor server conventions

The server is a JVM-only Ktor (Netty) app in `server/`. Base package: `com.greenzean.animekmp.server`. The server syncs libraries between a user's devices. It never calls MAL or any other platform and stores no platform tokens or platform user data (ADR 0007). Treat clients as untrusted.

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

`ContentNegotiation { json() }` (kotlinx.serialization), installed inside a `route("") { }` in `routing { }` so it does not cover the GraphQL routes (graphql-kotlin installs its own Jackson one). Respond with `call.respond(SomeSerializable(...))`.

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

### Docker for the Postgres tests

`AuthApiTest`, `SyncApiTest` and `MigrationTest` use Testcontainers and need a Docker daemon. The Mac does not run Docker; the daemon lives on another machine and is configured only in the developer's home directory, never in this repo (it is public):

- Run the server tests with the user's private `server-tests` command (in their `PATH`, outside the repo). It opens the connection to the remote Docker if needed and runs Gradle with the Testcontainers settings, for example `server-tests :server:test`. Its contents, addresses and users must not be copied into the repo, ADRs, tickets, PR text or commit messages.
- If it fails to reach Docker, stop and tell the user the remote Docker is unreachable; do not start Docker locally and do not skip the tests.
- The user's memory notes describe the private setup.

## Dependencies

Ktor, Logback and the logstash encoder versions live in `gradle/libs.versions.toml`; `server/build.gradle.kts` references the catalog only.

## Verify

```
./gradlew :server:build   # compile, tests, spotless, detekt
./gradlew :server:run     # then: curl localhost:8080/health
```

## GraphQL

graphql-kotlin (ADR 0003). `install(GraphQL)` in `module()` reads `graphql/GraphQLSchemaDefinition`; `Route.graphQLRoutes()` in `routes/` serves `POST /graphql` and GraphiQL at `/graphiql` (always on for now; no auth yet). Schema export and API-change order: `graphql-schema` skill.

## Auth (ADR 0010)

`auth/` holds `GoogleIdTokenVerifier`, `SessionTokens`, `AuthRepository` and `AuthService`; the GraphQL mutations are in `graphql/auth/`. `AppConfig.auth` (`GOOGLE_CLIENT_ID`, `JWT_SECRET`) is both-or-neither and required in prod. Auth failures throw `AuthFailure`: one generic client message, `extensions.code = UNAUTHENTICATED`, the reason only in the log. Tests use a fake `RSAKeyProvider` (`auth/FakeGoogleKeys.kt`), never Google's real keys.

## Not decided yet

Database access is covered by the `postgres-migrations` skill. 
