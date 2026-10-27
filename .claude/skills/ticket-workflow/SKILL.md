---
name: ticket-workflow
description: Ticket template, states, the Contract, the parts of a ticket and the parallel agent for each, Definition of Done, branch naming, plan format and the no-commit rule for this project. Use when planning, starting, reviewing or finishing any ticket (GitHub issue) in anime-kmp.
---

# Ticket workflow

Tickets are GitHub issues in `greenzean/anime-kmp`, one project per phase, one milestone per M-number.

## Types and sizing
Feature, Platform, Chore, Spike (time-boxed, produces a written answer), Bug. Each ticket is sized for 1 to 2 days.

## Template
- **Title:** what the user can do, at most 6 words.
- **Goal:** one sentence, from the user's point of view.
- **Where in the app:** the entry point (button, tab, Settings row). Server-only and release tickets say "nowhere".
- **Contract:** the items listed in Flow step 2 that the ticket needs (SDL change, State, Action and Event, interfaces, screen signatures, string keys and test tags). Fixed before work starts so the parts run in parallel (ADR 0015).
- **Acceptance:** short bullets.
- **Decisions:** answers to every question the ticket raises, settled when the ticket is written.
- **Depends on:** lower ticket numbers only.
- **Out of scope.**

Design criteria are in the Definition of Done, not repeated in each ticket.

## Ticket rules
- Every feature ticket ends usable: the user reaches it from a button, a tab or a Settings row. No API-only or view-only tickets; the API and the view that shows it are one ticket.
- Tickets are numbered in work order and depend only on lower numbers.
- Questions are answered when the ticket is written, in Decisions. When a ticket starts, ask only if the code contradicts it.
- Design is part of every view ticket (see Definition of Done). The design system ticket M2-6a (#86) is the only separate design ticket.

## States
Backlog, Ready, In Progress, In Review, Done. A ticket is not Done until it works on every platform it claims.

## Planning model
- The user's default is `opusplan` (Opus in plan mode, Sonnet after approval). Tickets labeled `opus-plan` (architecture-heavy: sync engine, auth, MAL OAuth, GraphQL schema) are planned in plan mode with it; other tickets can be planned on Sonnet.
- Execution is Sonnet. Switch models at the start of a session, not mid-session.

## Parts and agents
A ticket touches one or more parts. Each touched part has one agent:

| Part | Agent |
|---|---|
| `server/` | `server` |
| `shared/`, and `androidApp/` platform code such as WorkManager | `shared-kmp` |
| `composeApp/` screens and previews | `compose-ui` |
| Gradle, catalog, CI | `build-ci` |
| `docs/` | `docs` |
| All tests (`commonTest`, `androidHostTest`, `server/src/test`) | `tester` |

Support agents that own no code: `build-runner` (Gradle runs), `Explore` (search), `researcher` (versions, terms), `reviewer` and `security-reviewer` (only when the user asks).

## Flow
1. Read the issue (`gh issue view <n>`).
2. Plan, in the main session: the parts the ticket touches and the agent for each, the skills to load, the **Contract**, steps, how it will be verified. Take the Decisions from the ticket; ask only if the code contradicts them. Wait for the user's approval.
   - The Contract is what lets the parts run at the same time. It has: the SDL change; the view model State, Action and Event; the domain models and the use case and repository interfaces; the `XxxScreen(state, onAction)` signatures; the string resource keys and test tags of every control; and new dependencies. The agents do not change it; they report a needed change.
   - When the plan is approved, the main session writes the Contract to disk before starting any agent: the SDL change in the committed schema file, and the Kotlin files for the State, Action, Event, models, interfaces and screen signatures, with `TODO()` bodies where they must compile. The agents then fill them in and the `server` agent makes the code-first schema print exactly that SDL; the drift test in `server-tests` checks it after all the agents finish.
   - Who owns the wiring: `shared-kmp` writes the feature's Koin module, `compose-ui` writes the feature's nav graph, and the main session adds both to `initKoin`, `App.kt` and the two Koin graph tests.
3. Execute in parallel: start one agent per touched part in a single batch, all in the same working tree, each touching only its own part, plus `tester`, which writes the tests for all parts from the Contract. The agents run no Gradle tasks, `server-tests` or `server-deploy`: while the other agents are still writing, a build, lint or test run would fail on their half-written files and would deploy a half-finished tree. Everything is checked in step 4, once all of them have finished.
4. Integrate, in the main session:
   - Wait for all the agents, then do the wiring above.
   - Run lint, the full build and the tests with `build-runner`, and `server-tests :server:test` when `server/` changed (it deploys when they pass).
   - The tests were written before the code existed, so failures here are expected. Fix them or send them back to the owning agent.
5. A ticket that touches one part, or a tiny change, skips the agents: the main session does it alone.
6. `reviewer` and `security-reviewer` only if the user asks. Give them only the ticket number (or, for `security-reviewer`, the area to review), and the last `server-tests` result when `server/` changed.
7. `/clear` before the next ticket.
8. The user decides when to commit, push and open the PR. When they do, commit as in the `commit-split` skill.

## No-commit rule
The rule is in `CLAUDE.md`; permission rules prompt for commits, pushes and `gh pr` as a safety net.

## Branch naming
Include the ticket ID: `m0-1-create-monorepo`, `m1-5-push-pull-sync`.

## Definition of Done
- Builds in CI for all active targets.
- Tests pass; shared logic is covered in `commonTest`. `tester` writes them (the main session, for a single-part ticket).
- View models depend on use cases only, never on repositories or data sources (`kmp-presentation-mvi` skill).
- Every `composeApp` screen has `runComposeUiTest` tests for all its states, actions, accessibility labels and UX rules (`kmp-testing` skill, View Tests).
- Checked manually on a device or emulator.
- Reachable from the app: the user can get to the feature from a button, a tab or a Settings row.
- Views: built on the design system from M2-6a (#86), with no new hard-coded colors, sizes or text styles. A Compose preview for every state in light and dark. Touch targets of at least 48dp, text contrast checked, `contentDescription` or semantics on every control. Emulator screenshots in the PR.
- Tickets that touch `server/`: `server-tests` passed and the deploy to the mini PC is healthy.
- Reviewed and merged, with the ticket linked to the PR.

## Decisions
Architecture decisions get an ADR in `docs/decisions/`. Process: `adr` skill.
