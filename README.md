# Anime Tracker (KMP)

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

## License

MIT, see `LICENSE`.
