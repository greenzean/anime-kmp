# Anime Tracker (KMP)

Offline-first anime tracker. Kotlin Multiplatform + Compose Multiplatform app, Ktor + PostgreSQL server, GraphQL API (Apollo Kotlin client), SQLDelight local DB, Koin DI. Anime data from MyAnimeList. Open source, portfolio project. Phase 1 is Android; iOS and web come later.

## Working rules (always apply)

- **No commits, pushes, or PRs** until the user explicitly says so. Then follow the `commit-split` skill: several commits by layer, each one building on its own. No attribution lines.
- **Stage new files right away:** `git add` every file you create as soon as it exists. Staging is not committing.
- **Plan first, then execute.** Every task gets a plan; execution starts only after the user approves it. The plan names the skills it will use and, for a ticket, the parts it touches and the Contract.
- **Load the matching skill before writing code of its kind:** `kmp-presentation-mvi`, `kmp-compose-ui`, `kmp-navigation`, `kmp-di-koin`, `kmp-data-layer`, `kmp-testing`. Skills do not load by themselves.
- **One ticket per session.** Tickets are GitHub issues in `greenzean/anime-kmp`. Load the `ticket-workflow` skill when planning or starting a ticket: template, models, the Contract, the parallel agents (one per touched part, plus `tester`) and the Definition of Done.
- **Where things run:** only the Android app, the emulator and Gradle builds run on the Mac. The server, PostgreSQL and Docker (compose, Testcontainers) run on the mini PC: never start Docker Desktop or `docker compose` locally; Docker is denied for Claude Code. Server tests: `server-tests`, which runs `server-deploy` when they pass, without asking (ADR 0014). Host and address details stay out of the repo (it is public).
- Verify versions, prices, API terms and library support with a search; say what could not be verified.
- Keep changes scoped to the ticket (touch only the modules it names).

## Modules

- `androidApp/`: Android host (application module, `MainActivity`); required by AGP 9, which cannot combine KMP with `com.android.application`.
- `composeApp/`: Compose Multiplatform UI library (`commonMain`).
- `shared/`: KMP logic (`commonMain`): data layer, sync, Apollo operations, MAL client, importers, view models.
- `server/`: Ktor server, GraphQL schema/resolvers, Postgres, Flyway. Syncs libraries between a user's devices.
- `docs/`: ADRs in `docs/decisions/`, MVP scope in `docs/milestone2-mvp.md`, ticket CSV, notes.

Details of where things live: `codebase-overview` skill.

## Code rules

- Business logic, data layer, sync, API client and view models live in `commonMain`.
- View models depend on use cases (`shared/<feature>/domain`), never on repositories or data sources.
- Only multiplatform libraries (no Retrofit, Hilt; verify Room KMP support before using it).
- Platform APIs (secure storage, notifications, file picker, connectivity, background work) go behind interfaces / `expect/actual`.
- Local DB is the source of truth; the UI reads and writes only locally.
- Only the app talks to MAL and other platforms; the server never does and stores no platform tokens or platform user data (ADR 0007). The app works without an account.
- Treat the client as untrusted on our server. Never commit secrets (`.env`, keystores).
- **No comments in code:** no KDoc, no `//` or `/* */`, no SQL `--`. Names and tests carry the meaning; reasons go in ADRs and PR text. When you touch a file, remove the comments that are already in it. This overrides matching the comment style of surrounding code.

## Token habits

Default effort is medium. Prefer `gh` over a GitHub MCP server.
