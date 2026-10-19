---
name: codebase-overview
description: Module map, where things live, and naming conventions for the anime-kmp monorepo. Use when orienting in the codebase or deciding where new code belongs.
---

# Codebase overview

Gradle multi-module monorepo. Group and base package: `com.greenzean.animekmp`. Versions live in `gradle/libs.versions.toml`.

## Modules

- `composeApp/`: Compose Multiplatform UI, Android host. Screens and composables in `commonMain`; Android entry point in `androidMain`.
- `shared/`: KMP logic in `commonMain`: data layer, sync, Apollo operations, importers, view models. Platform APIs behind interfaces / `expect/actual`.
- `server/`: Ktor server (JVM): GraphQL schema/resolvers, Postgres, Flyway migrations, MAL adapter. Only the server talks to MAL.
- `docs/`: ADRs in `docs/decisions/`, ticket CSV, notes.

## Conventions

- Packages: `com.greenzean.animekmp.<module>.<feature>`.
- Local DB (SQLDelight) is the source of truth; the UI reads and writes only locally.
- Secrets go in `.env` (ignored); `.env.example` lists the keys.

## Status

After M0-1 the modules are build stubs (JVM target only). Android, Compose and real sources arrive in M0-2.
