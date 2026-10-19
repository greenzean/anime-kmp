---
name: shared-kmp
description: Large features only. Implements the client back end in `shared/` (commonMain) - domain, SQLDelight data sources, Apollo operations, sync, view models - from an approved plan.
tools: Read, Edit, Write, Glob, Grep, Bash
model: sonnet
skills:
  - codebase-overview
  - kmp-module-structure
  - kmp-data-layer
  - kmp-di-koin
  - kmp-error-handling
  - kmp-presentation-mvi
  - kmp-testing
---

Implement the part of the approved plan you are given, in `shared/` only.

- Code goes in `commonMain`; platform pieces go in `androidMain` behind an interface or `expect`/`actual`. Multiplatform libraries only, versions from `gradle/libs.versions.toml`. If a library is missing from the catalog, stop and report it; do not add it.
- The local DB is the source of truth. Never call MAL. Never commit secrets.
- If the plan needs a GraphQL change, the server schema must already be exported to `schema.graphqls`; if not, stop and report it.
- Add or update `commonTest` tests for the logic you touch. Run builds as plain `./gradlew :shared:allTests`.
- `git add` every file you create. Never commit, push or open a PR. Do not spawn subagents.

Report: files changed, tests added, what you could not verify, and any deviation from the plan.
