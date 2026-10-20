# Anime Tracker (KMP)

Offline-first anime tracker. Kotlin Multiplatform + Compose Multiplatform app, Ktor + PostgreSQL server, GraphQL API (Apollo Kotlin client), SQLDelight local DB, Koin DI. Anime data from MyAnimeList. Open source, portfolio project. Phase 1 is Android; iOS and web come later.

## Working rules (always apply)

- **No commits, pushes, or PRs** until the user explicitly says so.
- **Stage new files right away:** `git add` every file you create as soon as it exists, so it is tracked. Staging is not committing; the rule above still applies.
- **Plan first, then execute.** Every task gets a plan; execution starts only after the user approves it.
- **No attribution lines** in commit messages or PR descriptions (no `Co-Authored-By`, no "Generated with Claude Code").
- **One ticket per session.** Tickets are GitHub issues in `greenzean/anime-kmp`. Workflow, template and Definition of Done: `ticket-workflow` skill.
- **Models:** default is `sonnet` (plans and executes small tickets). Tickets labeled `opus-plan` are planned with `/model opusplan` (Opus in plan mode, Sonnet after approval). Switch at the start of a session, not mid-session.
- Verify versions, prices, API terms and library support with a search; say what could not be verified.
- Keep prompts and changes scoped to the ticket (touch only the modules it names).

## Modules

- `androidApp/`: Android host (application module, `MainActivity`); required by AGP 9, which cannot combine KMP with `com.android.application`.
- `composeApp/`: Compose Multiplatform UI library (`commonMain`).
- `shared/`: KMP logic (`commonMain`): data layer, sync, Apollo operations, MAL client, importers, view models.
- `server/`: Ktor server, GraphQL schema/resolvers, Postgres, Flyway. Syncs libraries between a user's devices.
- `docs/`: ADRs in `docs/decisions/`, MVP scope in `docs/milestone2-mvp.md`, ticket CSV, notes.

Details of where things live: `codebase-overview` skill.

## Code rules

- Business logic, data layer, sync, API client and view models live in `commonMain`.
- Only multiplatform libraries (no Retrofit, Hilt; verify Room KMP support before using it).
- Platform APIs (secure storage, notifications, file picker, connectivity, background work) go behind interfaces / `expect/actual`.
- Local DB is the source of truth; the UI reads and writes only locally.
- Only the app talks to MAL and other platforms; the server never does and stores no platform tokens or platform user data (ADR 0007). The app works without an account.
- Treat the client as untrusted on our server. Never commit secrets (`.env`, keystores).

## Delegation

The main session is the orchestrator and implements tickets itself. Subagents start cold and cost tokens, so use them only for:

- `build-runner`: Gradle builds and tests (reports only failures).
- `Explore`: codebase search.
- `researcher`: versions, prices, API terms.
- `docs`: formatting ADRs, README sections, ticket text from decisions already made.
- `reviewer`, `security-reviewer`: **only when the user asks.**
- Area agents (`shared-kmp`, `server`, `compose-ui`, `tester`, `build-ci`) only for large features. They run their own Gradle checks, because subagents cannot call `build-runner`.

Subagents never delegate further. Feature order for large features: server schema first, then app back end, then views (views in parallel only when the view model interface is fixed up front), then tests.

## Token habits

Default effort is medium. Prefer `gh` over a GitHub MCP server. `/clear` between unrelated tickets.
