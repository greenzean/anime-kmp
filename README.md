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

Copy `.env.example` to `.env` for server configuration. Never commit `.env`.

## License

MIT, see `LICENSE`.
