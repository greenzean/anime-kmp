# Anime Tracker (KMP)

[![CI](https://github.com/greenzean/anime-kmp/actions/workflows/ci.yml/badge.svg)](https://github.com/greenzean/anime-kmp/actions/workflows/ci.yml)

Offline-first anime tracker built with Kotlin Multiplatform and Compose Multiplatform. Anime data comes from MyAnimeList via a Ktor + PostgreSQL server with a GraphQL API. Phase 1 targets Android; iOS and web come later.

## Modules

- `composeApp/`: Compose Multiplatform UI, Android host.
- `shared/`: KMP logic (`commonMain`): data layer, sync, Apollo operations, importers, view models.
- `server/`: Ktor server, GraphQL schema/resolvers, Postgres, Flyway, MAL adapter.
- `docs/`: ADRs (`docs/decisions/`), tickets, notes.

## Build

```
./gradlew build
```

## Lint and format

```
./gradlew spotlessApply        # format
./gradlew spotlessCheck detekt # what CI runs
./gradlew installGitHooks      # optional pre-commit hook running the checks
```

Spotless (ktlint + Compose rules) handles formatting; detekt handles static analysis. Style settings are in `.editorconfig` and `config/detekt/detekt.yml`.

Copy `.env.example` to `.env` for server configuration. Never commit `.env`.

## Run the server

```
./gradlew :server:run
curl localhost:8080/health
```

Configured by env vars `HOST`, `PORT`, `APP_ENV` (`dev` | `prod`; `prod` logs JSON) and `LOG_LEVEL`. See `.env.example`.

## Local environment (Docker)

Starts PostgreSQL and the server with one command. Needs Docker with Compose v2.

```
cp .env.example .env     # optional: defaults work without it
docker compose up --build
curl localhost:8080/health
```

- The first build takes a few minutes (Gradle downloads dependencies); later builds reuse the cache.
- Postgres listens on `127.0.0.1:5432` (db, user and password default to `animekmp`; override with `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` in `.env`). Data lives in the `postgres-data` volume.
- The server does not use the database yet; Postgres is started and health-checked so it is ready for the DB tickets.
- Stop with `docker compose down`; add `-v` to also delete the database volume.
- To run the server from Gradle against the Compose database: `docker compose up postgres`, then `./gradlew :server:run`.

## License

MIT, see `LICENSE`.
