# 0001: AGP 9 module layout

- **Status:** accepted
- **Date:** 2026-10-19

## Context

AGP 9.4.0 no longer allows `org.jetbrains.kotlin.multiplatform` to be applied together with `com.android.application` in the same module. The Android host app and the Kotlin Multiplatform code therefore cannot share one Gradle module.

Source: https://kotlinlang.org/docs/multiplatform/multiplatform-project-agp-9-migration.html

Versions in use:

- Kotlin 2.4.20
- Compose Multiplatform 1.12.1
- AGP 9.4.0
- compile SDK 37 (required by Compose Multiplatform 1.12.1 dependencies), target SDK 36
- minSdk 26

## Decision

The repository uses three modules:

- `androidApp`: `com.android.application`. Contains `MainActivity` and depends on `composeApp`.
- `composeApp`: KMP library using `com.android.kotlin.multiplatform.library`. Compose UI lives in `commonMain`.
- `shared`: KMP library using `com.android.kotlin.multiplatform.library`. Holds the logic (data layer, sync, Apollo operations, importers, view models) in `commonMain`.

iOS targets are declared in `composeApp` and `shared`, but they are gated by the Gradle property `animekmp.enableIos`, which defaults to `false`.

The legacy Android variant API is not used.

## Consequences

- The debug-only dependency configuration is `androidRuntimeClasspath`. Dependencies that should only be present in debug builds are declared against that configuration.
- The Android entry point is separate from the shared UI and logic, so `composeApp` and `shared` remain platform-neutral libraries.
- iOS support can be turned on later without restructuring modules, by setting `animekmp.enableIos`.
