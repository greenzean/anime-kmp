---
name: kmp-module-structure
description: Module layout, package-by-feature rules, dependency rules, and Gradle setup (version catalog, AGP 9 module split) for this KMP project. Use this skill whenever deciding where a new class, feature, or module should live, asking "how should I structure this", adding a feature, adding a module, configuring Gradle, working with the version catalog, or making any decision about project-level architecture. Trigger on phrases like "add a module", "create a feature", "how should I structure", "project structure", "convention plugin", "libs.versions.toml", "where does X live", or "package layout".
---

# KMP Module Structure

## Core Philosophy

- **Flat modules, package-by-feature**: a small, fixed set of Gradle modules; features are packages inside them, not modules.
- **Clean Architecture layers** inside each feature: `presentation` → `domain` ← `data`. Domain is innermost and depends on nothing.
- **Use cases are the domain's public API.** The presentation layer never reaches into a repository or data source directly — it goes through a use case. See the `kmp-presentation-mvi` skill for the shape and rationale.
- **Code lives in a feature package unless it is needed by more than one feature** — then it moves to a `core` package.
- Features **never import each other's packages**. Cross-feature shared data belongs in `core.domain` (domain models) or `composeapp.core` (shared UI plumbing), not in the owning feature. Without a module boundary this is enforced by review, so check imports.
- Business logic, data layer, sync, API client and view models live in `commonMain`. Platform APIs (secure storage, notifications, file picker, connectivity, background work) go behind interfaces or `expect`/`actual`.

---

## Module Layout

```
androidApp/   Android host: com.android.application, Application class (startKoin), MainActivity, manifest
composeApp/   Compose Multiplatform UI library: screens, design system, navigation (commonMain)
shared/       KMP logic library: domain, data (SQLDelight, Apollo), sync, view models (commonMain)
server/       Ktor server (JVM): GraphQL schema/resolvers, Postgres, Flyway, MAL adapter
docs/         ADRs (docs/decisions), ticket CSV, notes
```

Dependency direction: `androidApp` → `composeApp` → `shared`. `server` is independent of the client modules. `shared` never depends on `composeApp` or `androidApp`.

AGP 9 does not allow `org.jetbrains.kotlin.multiplatform` together with `com.android.application`, which is why the Android host is its own module (see `docs/decisions/0001-agp9-module-layout.md`). `shared` and `composeApp` apply `com.android.kotlin.multiplatform.library` and configure Android in the `android { }` block (namespace, compileSdk, minSdk). Debug-only Android dependencies go in `androidApp`.

### Packages (base `com.greenzean.animekmp`)

```
shared/.../shared/core/{domain,data}                   Result, DataError, Apollo/Ktor factories, SQLDelight setup
shared/.../shared/<feature>/domain                     models, repository/data-source interfaces, use cases, error types
shared/.../shared/<feature>/data                       repository + data-source implementations, DTOs, mappers, SQLDelight/Apollo usage
shared/.../shared/<feature>/presentation               ViewModel, State, Action, Event, UI models
composeApp/.../composeapp/core                         UiText, toUiText() mappers, ObserveAsEvents
composeApp/.../composeapp/designsystem                 theme, colors, typography, reusable components
composeApp/.../composeapp/navigation                   NavHost and route wiring
composeApp/.../composeapp/<feature>                    Root/Screen composables for the feature, feature nav graph
```

Platform code: `androidMain`/`iosMain` source sets in `shared` and `composeApp` (bridged from `commonMain` via interfaces or `expect`/`actual`). Shared tests in `commonTest`.

Add a new Gradle module only for a standalone concern with real complexity (multiple classes, its own config, a non-trivial API) that more than one module needs. Do not add a module for a single class or a trivial utility.

---

## Dependency Rules (by package)

| Layer | May depend on |
|---|---|
| `presentation` | `domain` (own feature), `core.domain` |
| `data` | `domain` (own feature), `core.domain`, `core.data` |
| `domain` | `core.domain` only — never `data` or `presentation` |
| `composeApp` screens | `shared` presentation (view models, state), `designsystem` |
| `androidApp` | everything (wires modules, Koin, Android entry points) |

**Every** layer may access `core.domain`.

---

## Gradle and Versions

- Use the **version catalog** (`gradle/libs.versions.toml`) for all versions, libraries and plugins. No hardcoded versions in build files.
- Apply plugins via `alias(libs.plugins.…)`; modules are referenced with type-safe accessors (`projects.shared`).
- iOS targets (`iosArm64`, `iosSimulatorArm64`) are declared but gated by the Gradle property `animekmp.enableIos` (default `false`). Web comes later.
- No convention plugins / `build-logic` yet. Introduce them when three or more modules repeat the same non-trivial config.
- Verify versions and library support with a search before adding a dependency, and say what could not be verified.

---

## Key Libraries

| Concern | Library |
|---|---|
| DI | Koin |
| API | GraphQL via Apollo Kotlin |
| Networking | Ktor Client (OkHttp on Android, Darwin on iOS) |
| Local DB | SQLDelight (source of truth); Room KMP only after verifying support |
| Secrets / tokens | Secure storage behind an interface (`expect`/`actual`) |
| Navigation | Compose Multiplatform Navigation (type-safe) |
| Serialization | KotlinX Serialization |
| Async | Coroutines + Flow |
| Background tasks | WorkManager on Android, behind an interface |
| Testing | `kotlin.test`, Turbine, AssertK, `kotlinx-coroutines-test` |
| UI testing | Compose Multiplatform UI test (`runComposeUiTest`) |

Image loading (Coil) and logging (Kermit) are fine choices when a ticket needs them. No Retrofit, no Hilt. Never commit secrets (`.env`, keystores).

---

## Checklist: Adding a New Feature

- [ ] Create the packages `shared.<feature>.{domain,data,presentation}` and `composeapp.<feature>`
- [ ] Domain: models, repository/data-source interfaces, error types, a use case for every data operation the UI will trigger
- [ ] Data: implementations, mappers, `.sq` queries / `.graphql` operations (see `kmp-data-layer`)
- [ ] Presentation: State/Action/Event/ViewModel in `shared`; Root/Screen composables in `composeApp` (see `kmp-presentation-mvi`)
- [ ] Register a Koin module for the feature (see `kmp-di-koin`) and a nav graph (see `kmp-navigation`)
- [ ] Verify no cross-feature imports and no `data` import from `presentation`
- [ ] Add tests in `commonTest` for use cases, repositories and view models
