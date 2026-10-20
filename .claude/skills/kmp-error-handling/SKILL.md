---
name: kmp-error-handling
description: |
  Generic Result wrapper, error types, and extension helpers for KMP (Android-first) - Result<T, E>, DataError, EmptyResult, map, onSuccess, onFailure, Apollo and SQLDelight error mapping. Use this skill whenever defining error types, creating a Result wrapper, handling success/failure flows, mapping errors, or working with typed errors anywhere in the app (not just data layer — also validation, auth, domain logic). Trigger on phrases like "Result wrapper", "error handling", "DataError", "onSuccess", "onFailure", "EmptyResult", "map result", "error type", "validation error", or "typed errors".
---

# KMP Error Handling

Core types live in `shared/commonMain`, package `shared.core.domain`, and network helpers in `shared.core.data`. Error-to-text mapping lives in `composeApp/commonMain` (`composeapp.core`), because string resources live there.

**State of the code (M1-4):** none of this exists yet. The first ticket with a fallible call (network or sync) creates `Result`, `DataError` and the helpers. The `HttpClient` helpers also need Ktor Client and a logger (Kermit or a small interface), neither of which is in the catalog yet. Base packages: `com.greenzean.animekmp.shared` and `com.greenzean.animekmp.composeapp`.

## Result Wrapper (`shared.core.domain`)

A generic, typed Result that works across all layers — data, domain, presentation, validation, anywhere a function can succeed or fail with a typed error.

```kotlin
interface Error

sealed interface Result<out D, out E : Error> {
    data class Success<out D>(val data: D) : Result<D, Nothing>
    data class Error<out E : com.greenzean.animekmp.shared.core.domain.Error>(val error: E) : Result<Nothing, E>
}

typealias EmptyResult<E> = Result<Unit, E>
```

---

## Extension Helpers (`shared.core.domain`)

These live alongside the `Result` definition:

```kotlin
inline fun <T, E : Error, R> Result<T, E>.map(
    map: (T) -> R
): Result<R, E> {
    return when (this) {
        is Result.Error -> Result.Error(error)
        is Result.Success -> Result.Success(map(this.data))
    }
}

inline fun <T, E : Error> Result<T, E>.onSuccess(
    action: (T) -> Unit
): Result<T, E> {
    return when (this) {
        is Result.Error -> this
        is Result.Success -> {
            action(this.data)
            this
        }
    }
}

inline fun <T, E : Error> Result<T, E>.onFailure(
    action: (E) -> Unit
): Result<T, E> {
    return when (this) {
        is Result.Error -> {
            action(error)
            this
        }
        is Result.Success -> this
    }
}

fun <T, E : Error> Result<T, E>.asEmptyResult(): EmptyResult<E> {
    return map { }
}
```

All helpers return `Result` so they can be chained:
```kotlin
repository.saveEntry(entry)
    .onSuccess { /* update UI */ }
    .onFailure { /* show error */ }
    .asEmptyResult()
```

---

## Shared Error Types (`shared.core.domain`)

### DataError

```kotlin
sealed interface DataError : Error {
    enum class Network : DataError {
        BAD_REQUEST,
        REQUEST_TIMEOUT,
        UNAUTHORIZED,
        FORBIDDEN,
        NOT_FOUND,
        CONFLICT,
        TOO_MANY_REQUESTS,
        NO_INTERNET,
        PAYLOAD_TOO_LARGE,
        SERVER_ERROR,
        SERVICE_UNAVAILABLE,
        SERIALIZATION,
        UNKNOWN
    }

    enum class Local : DataError {
        DISK_FULL,
        NOT_FOUND,
        UNKNOWN
    }
}
```

### Feature-Specific Errors

Features define their own error types by implementing `Error`:

```kotlin
enum class PasswordValidationError : Error {
    TOO_SHORT,
    NO_UPPERCASE,
    NO_DIGIT
}

// Used with Result — always a single error, not a list:
fun validatePassword(pw: String): EmptyResult<PasswordValidationError>
```

Multiple validation errors are not supported — always return a single error type per Result.

---

## Exception Handling Philosophy

Never throw exceptions for expected failures — always return `Result.Error`. Catch exceptions at the layer that is responsible for the exception:

| Exception origin | Catch in | Example |
|---|---|---|
| HTTP / network | Data layer | `UnresolvedAddressException` → `DataError.Network.NO_INTERNET` |
| Database / disk | Data layer | SQLDelight/driver exception (e.g. disk full) → `DataError.Local.DISK_FULL`. `SQLiteFullException` is Android-only, so catch `Exception` in `commonMain` and map by message, or map in a platform `expect`/`actual` helper |
| GraphQL / Apollo | Data layer | `response.exception` and `response.errors` → `DataError.Network` (see Apollo section) |
| Business logic | Domain layer | Invalid input → `Result.Error(ValidationError.TOO_SHORT)` |
| Presentation | Presentation layer | Catch and map to `Result.Error` at that layer |

The layer that owns the exception catches it and converts it to a typed `Result.Error`. Upper layers never see raw exceptions for expected failures.

---

## Mapping Errors to UiText

Every error type that is displayed to the user should have a `.toUiText()` extension function. Place it in:

- **The feature's `composeApp` package** (`composeapp.<feature>`) — if the error is feature-specific (e.g., `AuthError.toUiText()`)
- **`composeapp.core`** — if the error is shared across features (e.g., `DataError.toUiText()`)

ViewModels in `shared` keep the typed error in State/Event; the Root composable converts it (see **kmp-presentation-mvi**).

If an error is purely internal and never shown to the user (e.g., a retry signal, an internal state marker), it does not need a `.toUiText()` mapping.

```kotlin
// composeApp: composeapp.core
fun DataError.toUiText(): UiText {
    return when (this) {
        DataError.Network.NO_INTERNET -> UiText.StringResource(Res.string.error_no_internet)
        DataError.Network.SERVER_ERROR -> UiText.StringResource(Res.string.error_server)
        DataError.Network.UNAUTHORIZED -> UiText.StringResource(Res.string.error_unauthorized)
        DataError.Local.DISK_FULL -> UiText.StringResource(Res.string.error_disk_full)
        // ... map all user-facing cases
        else -> UiText.StringResource(Res.string.error_unknown)
    }
}
```

---

## Apollo Error Mapping (`shared.core.data`)

An Apollo call does not throw for network or GraphQL failures; it returns them on the response. Map both to `DataError.Network`:

```kotlin
inline fun <D : Operation.Data, T> ApolloResponse<D>.toResult(
    map: (D) -> T
): Result<T, DataError.Network> {
    exception?.let { return Result.Error(it.toDataError()) }          // transport / parse failure
    if (hasErrors()) return Result.Error(errors.orEmpty().toDataError()) // GraphQL errors
    val data = data ?: return Result.Error(DataError.Network.UNKNOWN)
    return Result.Success(map(data))
}

fun ApolloException.toDataError(): DataError.Network = when (this) {
    is ApolloNetworkException -> DataError.Network.NO_INTERNET
    is ApolloHttpException -> statusCode.toDataError()                  // reuse the HTTP status mapping below
    is JsonDataException, is ApolloParseException -> DataError.Network.SERIALIZATION
    else -> DataError.Network.UNKNOWN
}
```

Map GraphQL error `extensions.code` values the server defines (e.g. `UNAUTHENTICATED` → `UNAUTHORIZED`, `NOT_FOUND`) in `List<Error>.toDataError()`; unknown codes become `UNKNOWN`. Check the exact exception class names against the Apollo Kotlin version in `libs.versions.toml` when you write this. Rethrow `CancellationException`.

---

## Safe Call Helpers (`shared.core.data`)

For plain HTTP calls (not GraphQL), typed extension functions on `HttpClient` wrap Ktor calls and map HTTP responses to `Result<T, DataError.Network>`:

```kotlin
suspend inline fun <reified Response : Any> HttpClient.get(
    route: String,
    queryParameters: Map<String, Any?> = mapOf()
): Result<Response, DataError.Network> {
    return safeCall {
        get {
            url(constructRoute(route))
            queryParameters.forEach { (key, value) ->
                parameter(key, value)
            }
        }
    }
}

suspend inline fun <reified Request, reified Response : Any> HttpClient.post(
    route: String,
    body: Request
): Result<Response, DataError.Network> {
    return safeCall {
        post {
            url(constructRoute(route))
            setBody(body)
        }
    }
}

suspend inline fun <reified Response : Any> HttpClient.delete(
    route: String,
    queryParameters: Map<String, Any?> = mapOf()
): Result<Response, DataError.Network> {
    return safeCall {
        delete {
            url(constructRoute(route))
            queryParameters.forEach { (key, value) ->
                parameter(key, value)
            }
        }
    }
}

suspend inline fun <reified T> safeCall(
    execute: () -> HttpResponse
): Result<T, DataError.Network> {
    val response = try {
        execute()
    } catch (e: UnresolvedAddressException) {
        Logger.e(throwable = e) { "No internet connection" }
        return Result.Error(DataError.Network.NO_INTERNET)
    } catch (e: SerializationException) {
        Logger.e(throwable = e) { "Serialization failed" }
        return Result.Error(DataError.Network.SERIALIZATION)
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        Logger.e(throwable = e) { "Unknown network error" }
        return Result.Error(DataError.Network.UNKNOWN)
    }

    return responseToResult(response)
}

suspend inline fun <reified T> responseToResult(
    response: HttpResponse
): Result<T, DataError.Network> {
    return when (response.status.value) {
        in 200..299 -> Result.Success(response.body<T>())
        401 -> Result.Error(DataError.Network.UNAUTHORIZED)
        408 -> Result.Error(DataError.Network.REQUEST_TIMEOUT)
        409 -> Result.Error(DataError.Network.CONFLICT)
        413 -> Result.Error(DataError.Network.PAYLOAD_TOO_LARGE)
        429 -> Result.Error(DataError.Network.TOO_MANY_REQUESTS)
        in 500..599 -> Result.Error(DataError.Network.SERVER_ERROR)
        else -> Result.Error(DataError.Network.UNKNOWN)
    }
}

fun constructRoute(route: String): String {
    return when {
        route.contains(BASE_URL) -> route
        route.startsWith("/") -> BASE_URL + route
        else -> BASE_URL + "/$route"
    }
}
```

Usage in a data source is clean and uniform:
```kotlin
suspend fun getStatus(): Result<StatusDto, DataError.Network> {
    return httpClient.get(route = "/status")
}
```

`BASE_URL` comes from configuration injected per build type, not a committed secret. `Logger` is Kermit; if the project has not adopted it yet, use a small logging interface in `shared.core`.

---

## When to Use What

| Scenario | Error type | Example return |
|---|---|---|
| Network call (Apollo or Ktor) | `DataError.Network` | `Result<List<LibraryEntry>, DataError.Network>` |
| Local DB access (SQLDelight) | `DataError.Local` | `Result<LibraryEntry, DataError.Local>` |
| Repository (multi-source) | `DataError` (supertype) | `Result<List<LibraryEntry>, DataError>` |
| Domain validation | Custom `Error` enum | `EmptyResult<PasswordValidationError>` |
| Auth logic | Custom `Error` enum | `Result<User, AuthError>` |

The `Result` wrapper is not limited to the data layer — use it anywhere a function has typed success and failure outcomes.
