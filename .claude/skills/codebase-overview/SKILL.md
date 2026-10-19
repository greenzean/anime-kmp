---
name: codebase-overview
description: Module map, where things live, and naming conventions for the anime-kmp monorepo. Use when orienting in the codebase or deciding where new code belongs.
---

# Codebase overview

Gradle multi-module monorepo. Group and base package: `com.greenzean.animekmp`. Versions live in `gradle/libs.versions.toml`.

## Modules

- `androidApp/`: Android host (`com.android.application`): manifest, `MainActivity`. AGP 9 forbids KMP plus application plugin in one module.
- `composeApp/`: Compose Multiplatform UI library. Screens and composables in `commonMain`.
- `shared/`: KMP logic in `commonMain`: data layer, sync, Apollo operations, importers, view models. Platform APIs behind interfaces / `expect/actual`.
- `server/`: Ktor server (JVM): GraphQL schema/resolvers, Postgres, Flyway migrations, MAL adapter. Only the server talks to MAL.
- `docs/`: ADRs in `docs/decisions/`, ticket CSV, notes.

## Conventions

- Packages: `com.greenzean.animekmp.<module>.<feature>`.
- Local DB (SQLDelight) is the source of truth; the UI reads and writes only locally.
- Server: `ktor-server-conventions` skill.
- Architecture and patterns: `kmp-*` skills (module-structure, data-layer, presentation-mvi, di-koin, navigation, compose-ui, error-handling, testing).
- Secrets go in `.env` (ignored); `.env.example` lists the keys.

## Status

After M0-2 the Android skeleton builds (`androidApp` -> `composeApp` -> `shared`). iOS targets are declared but gated by `animekmp.enableIos` (default false). `server` is a runnable Ktor skeleton (`GET /health`); see `ktor-server-conventions`.
