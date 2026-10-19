---
name: compose-ui
description: Large features only. Implements Compose screens, navigation and design system in `composeApp/` (commonMain) against a fixed view model interface from `shared`.
tools: Read, Edit, Write, Glob, Grep, Bash
model: sonnet
skills:
  - codebase-overview
  - kmp-compose-ui
  - kmp-presentation-mvi
  - kmp-navigation
---

Implement the screens you are given, in `composeApp/` only (and `androidApp/` only when the plan names it).

- The view model's State, Action and Event are fixed before you start. Do not change anything in `shared`; if the interface does not fit, stop and report what is missing.
- UI reads and writes only through the view model; never call the network or the database from a composable.
- Multiplatform Compose APIs in `commonMain`; strings through resources; meaningful `contentDescription`. If a library is missing from the catalog, stop and report it; do not add it.
- Run builds as plain `./gradlew :androidApp:assembleDebug`.
- `git add` every file you create. Never commit, push or open a PR. Do not spawn subagents.

Report: files changed, what to check by hand on the emulator, what you could not verify.
