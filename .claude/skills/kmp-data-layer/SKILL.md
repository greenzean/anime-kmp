---
name: kmp-data-layer
description: |
  Data layer patterns for KMP (Android-first) - data sources, repositories, DTOs, mappers, SQLDelight local database, Apollo GraphQL client, Ktor HttpClient, safe call helpers, token storage, and offline-first sync. Use this skill whenever writing or reviewing a data source or repository, creating DTOs or SQLDelight queries, writing mappers, setting up the Apollo or Ktor client, handling network errors, or implementing token refresh. Trigger on phrases like "create a repository", "create a data source", "add a query", "SQLDelight", "Apollo", "GraphQL operation", "write a mapper", "DTO", "network call", "token storage", "sync", or "offline-first".
---

# KMP Data Layer

All data-layer code lives in `shared/commonMain`, in the `data` package of its feature: `com.greenzean.animekmp.shared.<feature>.data` (cross-feature pieces in `shared.core.data`). Only platform pieces (SQLDelight driver, Ktor engine, secure storage) go in `androidMain`/`iosMain` behind interfaces or `expect`/`actual`. Only multiplatform libraries: no Retrofit, no Room unless Room KMP support is verified first.

## Error Handling

This skill uses `Result<T, E>`, `DataError`, and the extension helpers defined in the **kmp-error-handling** skill. Refer to that skill for the full `Result` wrapper, `DataError` sealed interface, and `map`/`onSuccess`/`onFailure`/`asEmptyResult` extensions.

---

## Who Talks to Whom

- The UI reads and writes **only the local database** (source of truth). It never waits on the network.
- A sync component pushes local changes to the server and pulls remote changes into the local DB (Apollo operations).
- Only the **app** talks to MyAnimeList (ADR 0007). The server never calls MAL and stores no platform tokens or platform user data. The client is untrusted by our server.

---

## Data Source vs Repository

- **Data source** — accesses a single data source (local DB, remote API, file system). Most classes in the data layer are data sources.
- **Repository** — combines multiple data sources (e.g., a remote API + a local DB for offline-first). Only use the term "repository" when the class genuinely coordinates multiple sources.

```kotlin
interface AnimeLocalDataSource {
    fun observeLibrary(): Flow<List<LibraryEntry>>
    suspend fun upsert(entry: LibraryEntry): EmptyResult<DataError.Local>
}

interface AnimeRemoteDataSource {
    suspend fun pullChanges(since: Instant?): Result<List<LibraryEntry>, DataError.Network>
}

interface LibraryRepository {
    fun observeLibrary(): Flow<List<LibraryEntry>>
    suspend fun sync(): EmptyResult<DataError>
}
```

## Domain Layer Contracts

- Pure Kotlin — no Android/framework imports.
- Contains: domain models, data source/repository **interfaces**, error types, use cases.
- **Every data source or repository used by a ViewModel must have an interface in the feature's `domain` package** — enforces that `presentation` never depends on `data`, and enables testing.

---

## DTOs, Rows, and Domain Models

- Always separate: Apollo models / DTOs and SQLDelight rows (data layer) ↔ Domain Models (domain layer).
- Domain models never go directly into SQLDelight queries or Apollo operations as-is; map at the boundary.
- Mappers are simple extension functions living in the data layer next to the type they convert:

```kotlin
fun LibraryEntryFragment.toLibraryEntry(): LibraryEntry = LibraryEntry(id = id, status = status.toDomain(), ...)
fun LibraryEntryRow.toLibraryEntry(): LibraryEntry = ...
fun LibraryEntry.toInput(): LibraryEntryInput = ...
```

---

## Implementations

Name implementations for what makes them unique — never suffix with `Impl`.

### Data source (single source)

```kotlin
class SqlDelightAnimeDataSource(private val db: AnimeDatabase) : AnimeLocalDataSource {
    override fun observeLibrary(): Flow<List<LibraryEntry>> =
        db.libraryEntryQueries.selectAll()
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows -> rows.map { it.toLibraryEntry() } }
}
```

### Repository (multiple sources)

```kotlin
class OfflineFirstLibraryRepository(
    private val local: AnimeLocalDataSource,
    private val remote: AnimeRemoteDataSource,
) : LibraryRepository {
    override fun observeLibrary() = local.observeLibrary()

    override suspend fun sync(): EmptyResult<DataError> =
        remote.pullChanges(since = null)
            .onSuccess { entries -> entries.forEach { local.upsert(it) } }
            .asEmptyResult()
}
```

Use names like `SqlDelightAnimeDataSource`, `ApolloAnimeDataSource`, `OfflineFirstLibraryRepository`. The name should tell you what the class wraps or how it behaves.

---

## Apollo Kotlin — GraphQL Client

The API is GraphQL; use Apollo Kotlin operations for it. `.graphql` files live in `shared/src/commonMain/graphql`, and the generated models stay inside the data layer.

```kotlin
class ApolloAnimeDataSource(private val apollo: ApolloClient) : AnimeRemoteDataSource {
    override suspend fun pullChanges(since: Instant?): Result<List<LibraryEntry>, DataError.Network> {
        val response = apollo.query(PullChangesQuery(since = Optional.presentIfNotNull(since))).execute()
        return response.toResult { data -> data.changes.map { it.libraryEntryFragment.toLibraryEntry() } }
    }
}
```

`toResult` maps `response.exception` (network/parse failure) and `response.errors` (GraphQL errors) to `DataError.Network` — see the **kmp-error-handling** skill. Create the `ApolloClient` once in a factory and inject it via Koin; attach the auth header through an interceptor that reads the token from secure storage. Never put secrets in the client or in the repository.

---

## Ktor — HttpClient (non-GraphQL calls and as Apollo's engine)

Configure the client once. Accept the engine externally so tests can swap in a mock engine:

```kotlin
object HttpClientFactory {
    fun create(engine: HttpClientEngine): HttpClient = HttpClient(engine) {
        install(ContentNegotiation) { json() }
        install(Logging) { logger = Logger.DEFAULT; level = LogLevel.INFO }
        defaultRequest { contentType(ContentType.Application.Json) }
    }
}
```

Inject `HttpClient` via Koin. Keep `HttpClientFactory`, `safeCall`, and `responseToResult` in `commonMain` — they are pure Kotlin. The `HttpClientEngine` is the only per-platform piece: provide it from each platform's Koin module (`OkHttp` on Android via `ktor-client-okhttp`, `Darwin` on iOS via `ktor-client-darwin`).

Use `safeCall` / `responseToResult` helpers for plain HTTP calls (e.g. a token endpoint). See the **kmp-error-handling** skill for the full implementation.

---

## Token Storage

Tokens are secrets: store them behind an interface in the `domain` package and implement it per platform.

```kotlin
interface TokenStorage {
    suspend fun load(): Tokens?
    suspend fun save(tokens: Tokens)
    suspend fun clear()
}
```

Never store tokens in plain preferences, logs, or the SQLDelight database. Never commit keystores or `.env` files. Token refresh (401) is handled in the Apollo/Ktor auth layer that reads and writes `TokenStorage`.

---

## Persistence (SQLDelight)

`.sq` files, schema and queries live in `shared/src/commonMain/sqldelight`; only the driver is per-platform. Expose it through an `expect`/`actual` factory (or a platform Koin module) injected via Koin:

```kotlin
expect class DatabaseDriverFactory {
    fun create(): SqlDriver
}
```

Tests use an in-memory driver (JDBC in-memory on the JVM/Android host test, native in-memory on iOS).

## Migrations

Schema changes ship as numbered `.sqm` migration files (`1.sqm`, `2.sqm`, ...). Never edit a released `.sq` schema without a matching migration; verify migrations in CI with SQLDelight's `verifyMigrations`.

---

## Offline-First Sync

**The local database is the single source of truth.** Writes go to the DB first (marked as pending), reads are DB `Flow`s. A sync worker pushes pending changes (mutation) and pulls remote changes (query), then writes the result to the DB. The ViewModel never observes network responses directly. The background trigger is a platform API behind an interface (WorkManager on Android).

---

## Naming Conventions

| Thing | Convention | Example |
|---|---|---|
| Data source interface | `<Entity><Local/Remote>DataSource` | `AnimeLocalDataSource`, `AnimeRemoteDataSource` |
| Data source impl | describe what makes it unique | `SqlDelightAnimeDataSource`, `ApolloAnimeDataSource` |
| Repository interface | `<Entity>Repository` (multi-source only) | `LibraryRepository` |
| Repository impl | describe what makes it unique | `OfflineFirstLibraryRepository` |
| DTO / Apollo input | `<Model>Dto`, generated `<Operation>Query/Mutation` | `LibraryEntryDto` |
| SQLDelight row | generated `<Table>` / `<Query>Row` | `LibraryEntry`, `SelectAllRow` |
| Mapper | extension fun on source type | `fun LibraryEntryFragment.toLibraryEntry()` |

---

## Checklist: Adding a New Data Source or Repository

- [ ] Define domain model(s) in `shared.<feature>.domain`
- [ ] Define data source or repository interface in `shared.<feature>.domain`
- [ ] Define feature-specific error type(s) in `shared.<feature>.domain` (implement `Error`) — see **kmp-error-handling** skill
- [ ] Add `.sq` queries (and a `.sqm` migration if the schema changed) and `.graphql` operations
- [ ] Write mappers as extension functions in `shared.<feature>.data`
- [ ] Implement data source (single source) or repository (multi-source) in `shared.<feature>.data`, named for what makes it unique
- [ ] Register it in the feature's Koin module (see **kmp-di-koin**)
