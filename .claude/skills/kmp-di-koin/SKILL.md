---
name: kmp-di-koin
description: |
  Koin dependency injection setup for KMP (Android-first) - module definitions per feature layer, ViewModel injection, platform modules, assembly in initKoin and the androidApp Application class, and koinViewModel() in composables. Use this skill whenever setting up Koin, defining a DI module, providing a repository or ViewModel, injecting a dependency, or wiring modules in the Application class. Trigger on phrases like "set up Koin", "add a Koin module", "inject a dependency", "DI module", "koinViewModel", "provide a ViewModel", "startKoin", or "single/viewModel/factory".
---

# KMP Dependency Injection (Koin)

## Principles

- One Koin module per feature layer (`data`, `presentation`) — create it only if there are dependencies to provide. Modules are plain `val`s in the feature's package in `shared` (`shared.<feature>.data`, `shared.<feature>.presentation`).
- Modules are assembled once, in `initKoin` (`shared/commonMain`, `com.greenzean.animekmp.shared.InitKoin.kt`), and started from `androidApp`'s `AnimeApplication`. Feature packages never call `startKoin` themselves.
- In the catalog today: `koin-core` (`shared`) and `koin-android` (`shared` androidMain, `androidApp`). `viewModelOf` and `koinViewModel()` need the Koin Compose/ViewModel artifacts, which are not added yet: verify them with the `researcher` agent in the first ticket with a ViewModel.
- Platform-only dependencies (Ktor engine, SQLDelight driver, secure token storage, Android `Context`) come from a `platformModule` (`expect`/`actual`), so everything else stays in `commonMain`.
- In Compose Root composables, always inject ViewModels via `koinViewModel()`.

---

## Module Definitions

Prefer the constructor-reference overloads (`singleOf`, `viewModelOf`, `factoryOf`) — they are more concise and let Koin resolve parameters automatically. Only fall back to the lambda overloads (`single { ... }`, `viewModel { ... }`, `factory { ... }`) when constructor injection alone is not enough, e.g. when you need to call a factory method, pass a named/qualified dependency, or do post-construction setup.

### Data layer module

```kotlin
// shared.library.data — today the module binds only the local data source:
//   single<LibraryLocalDataSource> { SqlDelightLibraryDataSource(get()) }
// Target shape once remote data and sync exist:
val libraryDataModule = module {
    singleOf(::SqlDelightLibraryDataSource) { bind<LibraryLocalDataSource>() }
    singleOf(::ApolloLibraryDataSource) { bind<LibraryRemoteDataSource>() }
    singleOf(::OfflineFirstLibraryRepository) { bind<LibraryRepository>() }
}
```

### Presentation layer module

```kotlin
// shared.library.presentation
val libraryPresentationModule = module {
    viewModelOf(::LibraryViewModel)
    viewModelOf(::EntryDetailViewModel)
}
```

### Core data module

```kotlin
// shared.core.data — as in the code today (CoreDataModule.kt)
val coreDataModule = module {
    single { AnimeDatabase(get<SqlDriver>()) }   // SqlDriver comes from platformModule
    // Later (not built yet): single { ApolloClientFactory.create(...) }, TokenStorage-backed auth
}
```

---

## Assembly

Register all modules in a shared `initKoin` entry point in common code, and expose platform-only dependencies through an `expect`/`actual` `platformModule`:

```kotlin
// shared/commonMain (InitKoin.kt) — shared entry point, called by every platform
fun initKoin(config: KoinAppDeclaration? = null): KoinApplication =
    startKoin {
        config?.invoke(this)
        modules(
            platformModule,
            // core
            coreDataModule,
            // features
            libraryDataModule,
            // libraryPresentationModule, ... once they exist
        )
    }

// shared/commonMain (core.data) — platform-only dependencies
expect val platformModule: Module
```

```kotlin
// shared/androidMain — as in the code today: the platform module provides the SqlDriver
actual val platformModule = module {
    single<SqlDriver> {
        val context = androidContext()
        DatabaseDriverFactory(context, seedDevData = context.isDebuggable()).create()
    }
    // Later: HttpClientEngine, TokenStorage (Android Keystore) — see kmp-data-layer
}
```

```kotlin
// androidApp — androidContext lives only in the Android host
class AnimeApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin { androidContext(this@AnimeApplication) }
    }
}
```

`AnimeApplication` is registered in `androidApp`'s `AndroidManifest.xml`. Add `androidLogger()` only when debugging DI.

When iOS is enabled (`animekmp.enableIos`), add an `iosMain` `platformModule` (`Darwin.create()`, native driver, Keychain storage) and expose `fun initKoinIos() = initKoin()` for Swift. Only the Android `Application` passes `androidContext`/`androidLogger`.

---

## Injecting in Composables

Always use `koinViewModel()` in Root composables (in `composeApp`):

```kotlin
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun LibraryRoot(
    onNavigateToDetail: (String) -> Unit,
    viewModel: LibraryViewModel = koinViewModel()
) { ... }
```

Never pass ViewModels down the composable tree — inject at the Root level only.

---

## Scoping Rules

| Scope | Preferred form | Fallback form | When to use |
|---|---|---|---|
| Singleton | `singleOf(::Impl) { bind<Interface>() }` | `single { ... }` | One instance for the app lifetime (repositories, HttpClient, ApolloClient, DB) |
| ViewModel | `viewModelOf(::MyViewModel)` | `viewModel { ... }` | ViewModel instances scoped to their lifecycle |
| Factory | `factoryOf(::Impl)` | `factory { ... }` | New instance on every injection (rare — prefer singleton or ViewModel) |

Use the `*Of` constructor-reference form by default. Only use the lambda form when you cannot express the binding with a constructor reference (factory methods, named qualifiers, manual setup).

---

## Naming Conventions

| Thing | Convention | Example |
|---|---|---|
| Koin module | `<feature><Layer>Module` | `libraryDataModule`, `libraryPresentationModule` |

---

## Checklist: Adding DI for a New Feature

- [ ] Define `val <feature>DataModule = module { ... }` in `shared.<feature>.data`
- [ ] Define `val <feature>PresentationModule = module { ... }` in `shared.<feature>.presentation`
- [ ] Register both modules in `initKoin { modules(...) }`
- [ ] Put any platform-only dependency in `platformModule`, not in a feature module
- [ ] Use `koinViewModel()` in all Root composables
- [ ] Verify the Koin module graph in a `commonTest` (`checkModules`/`verify`) once the feature is wired
